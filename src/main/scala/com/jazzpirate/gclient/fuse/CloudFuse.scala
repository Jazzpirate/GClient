package com.jazzpirate.gclient.fuse

import java.nio.ByteBuffer
import java.nio.file.Paths

import com.jazzpirate.gclient.Settings
import com.jazzpirate.gclient.hosts.{Account, CloudDirectory, CloudFile}
import info.kwarc.mmt.api.utils.File
import jnr.ffi.Platform.OS.WINDOWS
import jnr.ffi.{Platform, Pointer}
import jnr.ffi.types.{dev_t, gid_t, mode_t, off_t, size_t, u_int32_t, uid_t}
import ru.serce.jnrfuse.{ErrorCodes, FuseFillDir, FuseStubFS, NotImplemented}
import ru.serce.jnrfuse.struct.{FileStat, Flock, FuseBufvec, FuseFileInfo, FusePollhandle, Statvfs, Timespec}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object FileNonExistent extends Throwable

class CloudFuse(var account: Account, var root: List[String], var id: String, var mountFolder: File) extends FuseStubFS {
  def mountCloud(): Unit = {
    if (!this.mountFolder.toJava.exists && Platform.getNativePlatform.getOS != WINDOWS) this.mountFolder.toJava.mkdirs
    this.mount(Paths.get(this.mountFolder.toString), true, false)
  }

  private def getFromAccount(path: String) = {
    // val buffer = scala.collection.JavaConverters.asScalaBuffer(Arrays.asList(path.split("\\/")))
    account.getFile(root ::: path.split("\\/").toList :_*)
  }

  private def getPath(path:String) = path match {
    case _ if path.startsWith("/.Trash-1000/info") => "/.Trash" + path.drop("/.Trash-1000/info".length)
    case _ if path.startsWith("/.Trash-1000/files") => "/.Trash" + path.drop("/.Trash-1000/files".length)
    case _ if path.startsWith("/.Trash-1000") => "/.Trash" + path.drop("/.Trash-1000".length)
    case _ => path
  }

  override def getattr(opath: String, stat: FileStat): Int = {
    val path = getPath(opath)
    try { // filemap(path)
      val f = getFromAccount(path)
      if (f.isInstanceOf[CloudDirectory]) {
        stat.st_mode.set(FileStat.S_IFDIR | 0x1ff)
        stat.st_uid.set(getContext.uid.get)
        stat.st_gid.set(getContext.gid.get)
      }
      else {
        stat.st_mode.set(FileStat.S_IFREG | 0x124)
        stat.st_size.set(f.size)
        stat.st_uid.set(getContext.uid.get)
        stat.st_gid.set(getContext.gid.get)
      }
    } catch {
      case FileNonExistent =>
        return -ErrorCodes.ENOENT
      case t : Throwable =>
        t.printStackTrace()
        ???
    }
    0
  }

  override def mkdir(path: String, @mode_t mode: Long) = {
    0
  }

  override def unlink(path: String) = {
    account.delete(path.split('/'):_*)
    0
  }

  override def rmdir(path: String) = {
    0
  }

  override def rename(path: String, newName: String) = {
    account.rename(newName.split('/').toList,path.split('/'):_*)
    0
  }

  override def truncate(path: String, offset: Long) = {
    0
  }

  override def open(path: String, fi: FuseFileInfo) = 0

  override def read(path: String, buf: Pointer, @size_t size: Long, @off_t offset: Long, fi: FuseFileInfo) = {
    val file = getFromAccount(path)
    val toRead = Math.min(file.size - offset, size).toInt
    val ret = file.read(toRead,offset)
    buf.put(0,ret,0,ret.length)
    toRead
  }

  override def write(path: String, buf: Pointer, @size_t size: Long, @off_t offset: Long, fi: FuseFileInfo) = try {
    //val maxWriteIndex = (offset + size).toInt
    val bytesToWrite = new Array[Byte](size.toInt)
    buf.get(0,bytesToWrite,0,size.toInt)
    val ret = account.write(offset,bytesToWrite,path.split('/'):_*).toInt
    println(ret)
    ret
  } catch {
    case FileNonExistent =>
      -ErrorCodes.ENOENT
    case t:Throwable =>
      t.printStackTrace()
      size.toInt
  }

  override def statfs(path: String, stbuf: Statvfs): Int = {
    if (Platform.getNativePlatform.getOS eq WINDOWS) { // statfs needs to be implemented on Windows in order to allow for copying
      // data from other devices because winfsp calculates the volume size based
      // on the statvfs call.
      // see https://github.com/billziss-gh/winfsp/blob/14e6b402fe3360fdebcc78868de8df27622b565f/src/dll/fuse/fuse_intf.c#L654
      if ("/" == path) {
        stbuf.f_blocks.set(1024 * 1024) // total data blocks in file system

        stbuf.f_frsize.set(1024) // fs block size

        stbuf.f_bfree.set(1024 * 1024) // free blocks in fs

      }
    }
    super.statfs(path, stbuf)
  }

  override def readdir(opath: String, buf: Pointer, filter: FuseFillDir, @off_t offset: Long, fi: FuseFileInfo): Int = {
    val path = getPath(opath)
    try {
      val f = getFromAccount(path).asInstanceOf[CloudDirectory]
      filter.apply(buf, ".", null, 0)
      filter.apply(buf, "..", null, 0)
      f.children.foreach((c: CloudFile) => filter.apply(buf, c.name, null, 0)) //(c => filter.apply(buf,c.name,null,0))
    } catch {
      case FileNonExistent =>
        return -ErrorCodes.ENOENT
      case t:Throwable =>
        t.printStackTrace()
        ???
    }
    0
  }

  override def create(path: String, @mode_t mode: Long, fi: FuseFileInfo): Int = {
    if(path.startsWith("/.Trash-1000")) return 0
    try {
      getFromAccount(path)
      -ErrorCodes.EEXIST()
    } catch {
      case FileNonExistent =>
        account.createFile(path.split('/'):_*)
        0
      case _ =>
        ???
    }
  }
}


/*
class CloudFuse(account:Account,root:List[String],id:String,mountFolder:File) extends FuseStubFS {
  def mountCloud = {
    if (!mountFolder.exists()) mountFolder.mkdirs()
    mount(Paths.get(mountFolder.toString),true,false)
  }
  private def getFromAccount(path:String) = account.getFile(root ::: path.split('/').toList :_*)

  override def getattr(path: String, stat: FileStat): Int = {
    import ru.serce.jnrfuse.ErrorCodes
    val f = try {
      // filemap(path)
      getFromAccount(path)
    } catch {
      case FileNonExistent =>
        return -ErrorCodes.ENOENT()
    }
    f match {
      case _: CloudDirectory =>
        stat.st_mode.set(FileStat.S_IFDIR | 0x1ed)
        stat.st_uid.set(getContext.uid.get)
        stat.st_gid.set(getContext.gid.get)
      case f =>
        stat.st_mode.set(FileStat.S_IFREG | 0x124)
        stat.st_size.set(f.size)
        stat.st_uid.set(getContext.uid.get)
        stat.st_gid.set(getContext.gid.get)
    }
    0
  }

  override def readlink(path: String, buf: Pointer, @size_t size: Long): Int = {
    0
  }

  override def mknod(path: String, @mode_t mode: Long, @dev_t rdev: Long): Int = {
    create(path, mode, null)
  }


  override def mkdir(path: String, @mode_t mode: Long): Int = {
    0
  }

  override def unlink(path: String) : Int = {
    0
  }

  override def rmdir(path: String): Int = {
    0
  }

  override def symlink(oldpath: String, newpath: String):Int = {
    0
  }

  override def rename(oldpath: String, newpath: String): Int = {
    0
  }

  override def link(oldpath: String, newpath: String) = {
    0
  }

  override def chmod(path: String, @mode_t mode: Long): Int = {
    0
  }

  override def chown(path: String, @uid_t uid: Long, @gid_t gid: Long): Int = {
    0
  }

  override def truncate(path: String, @off_t size: Long): Int = {
    0
  }

  override def open(path: String, fi: FuseFileInfo): Int = {
    0
  }

  override def read(path: String, buf: Pointer, @size_t size: Long, @off_t offset: Long, fi: FuseFileInfo): Int = {
    val file = getFromAccount(path)
    val toRead = Math.min(file.size - offset, size).toInt
    val ret = file.read(toRead,offset)
    buf.put(0,ret,0,ret.length)
    toRead
  }

  override def write(path: String, buf: Pointer,@size_t size: Long,@off_t offset: Long, fi: FuseFileInfo): Int = {
    0
  }

  // @NotImplemented
  override def statfs(path: String, stbuf: Statvfs) = {
    if (Platform.getNativePlatform.getOS eq WINDOWS) { // statfs needs to be implemented on Windows in order to allow for copying
      // data from other devices because winfsp calculates the volume size based
      // on the statvfs call.
      // see https://github.com/billziss-gh/winfsp/blob/14e6b402fe3360fdebcc78868de8df27622b565f/src/dll/fuse/fuse_intf.c#L654
      if ("/" == path) {
        stbuf.f_blocks.set(1024 * 1024) // total data blocks in file system

        stbuf.f_frsize.set(1024) // fs block size

        stbuf.f_bfree.set(1024 * 1024) // free blocks in fs

      }
    }
    super.statfs(path, stbuf)
  }

  override def flush(path: String, fi: FuseFileInfo) = {
    0
  }

  override def release(path: String, fi: FuseFileInfo) = {
    0
  }

  override def fsync(path: String, isdatasync: Int, fi: FuseFileInfo) = {
    0
  }

  override def setxattr(path: String, name: String, value: Pointer, @size_t size: Long, flags: Int) = {
    0
  }

  override def getxattr(path: String, name: String, value: Pointer, @size_t size: Long) = {
    0
  }

  override def listxattr(path: String, list: Pointer, @size_t size: Long) = {
    0
  }

  override def removexattr(path: String, name: String) = {
    0
  }

  override def readdir(path: String, buf: Pointer, filter: FuseFillDir,@off_t offset: Long, fi: FuseFileInfo): Int = {
    val f = try {
      getFromAccount(path).asInstanceOf[CloudDirectory]
      //filemap(path).asInstanceOf[CloudDirectory]
    } catch {
      case FileNonExistent =>
        return -ErrorCodes.ENOENT()
      case t:Throwable =>
        ???
    }
    filter.apply(buf, ".", null, 0)
    filter.apply(buf, "..", null, 0)
    f.synchronized{f.children}.foreach(c => filter.apply(buf,c.name,null,0))
    0
  }

  override def releasedir(path: String, fi: FuseFileInfo) = {
    0
  }

  override def fsyncdir(path: String, fi: FuseFileInfo) = {
    0
  }

  override def destroy(initResult: Pointer): Unit = {
    print("")
  }

  //private def filemap(path : String) = synchronized{ _filemap.getOrElseUpdate(path,getFromAccount(path)) }
  //private val _filemap = mutable.HashMap.empty[String,CloudFile]

  @Override
  override def create(path: String, @mode_t mode: Long, fi: FuseFileInfo) = {
    println("here")
    0
  }

  override def ftruncate(path: String, @off_t size: Long, fi: FuseFileInfo): Int = {
    truncate(path, size)
  }

  override def fgetattr(path: String, stbuf: FileStat, fi: FuseFileInfo): Int = {
    getattr(path, stbuf)
  }

  override def lock(path: String, fi: FuseFileInfo, cmd: Int, flock: Flock): Int = {
    -ErrorCodes.ENOSYS
  }

  override def utimens(path: String, timespec: Array[Timespec]): Int = {
    -ErrorCodes.ENOSYS
  }

  override def bmap(path: String, @size_t blocksize: Long, idx: Long) = {
    0
  }

  override def ioctl(path: String, cmd: Int, arg: Pointer, fi: FuseFileInfo, @u_int32_t flags: Long, data: Pointer): Int = {
    -ErrorCodes.ENOSYS
  }

  override def poll(path: String, fi: FuseFileInfo, ph: FusePollhandle, reventsp: Pointer): Int = {
    -ErrorCodes.ENOSYS
  }

  // @NotImplemented
  override def write_buf(path: String, buf: FuseBufvec, @off_t off: Long, fi: FuseFileInfo): Int = {
    // super.write_buf(path,buf,off,fi)
    null.asInstanceOf[Int]
  }

  // @NotImplemented
  override def read_buf(path: String, bufp: Pointer, @size_t size: Long, @off_t off: Long, fi: FuseFileInfo): Int = {
    null.asInstanceOf[Int]
  }

  override def flock(path: String, fi: FuseFileInfo, op: Int): Int = {
    -ErrorCodes.ENOSYS
  }

  override def fallocate(path: String, mode: Int, @off_t off: Long, @off_t length: Long, fi: FuseFileInfo): Int = {
    -ErrorCodes.ENOSYS
  }
}
*/