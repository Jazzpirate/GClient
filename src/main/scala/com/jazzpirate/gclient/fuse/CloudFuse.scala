package com.jazzpirate.gclient.fuse

import java.nio.file.Paths

import com.jazzpirate.gclient.Settings
import com.jazzpirate.gclient.hosts.{Account, CloudDirectory}
import info.kwarc.mmt.api.utils.File
import jnr.ffi.Pointer
import ru.serce.jnrfuse.{ErrorCodes, FuseFillDir, FuseStubFS}
import ru.serce.jnrfuse.struct.{FileStat, FuseFileInfo}

import scala.jdk.CollectionConverters._

object FileNonExistent extends Throwable

class CloudFuse(account:Account,root:List[String],id:String,mountFolder:File) extends FuseStubFS {
  def mountCloud = {
    if (!mountFolder.exists()) mountFolder.mkdirs()
    mount(Paths.get(mountFolder.toString),true,true)
  }
  private def get(path:String) = account.getFile(root ::: path.split('/').toList :_*)

  override def getattr(path: String, stat: FileStat): Int = {
    import ru.serce.jnrfuse.ErrorCodes
    val f = try {
      get(path)
    } catch {
      case FileNonExistent =>
        return -ErrorCodes.ENOENT()
    }
    f match {
      case d: CloudDirectory =>
        stat.st_mode.set(FileStat.S_IFDIR | 0x1ed)
      case f =>
        stat.st_mode.set(FileStat.S_IFREG | 0x124)
        stat.st_size.set(f.size)
    }
    0
  }

  override def mkdir(path: String, mode: Long): Int = {
    0
  }

  override def rmdir(path: String): Int = {
    0
  }

  override def rename(oldpath: String, newpath: String): Int = {
    0
  }

  override def chmod(path: String, mode: Long): Int = {
    0
  }

  override def chown(path: String, uid: Long, gid: Long): Int = {
    0
  }

  override def truncate(path: String, size: Long): Int = {
    0
  }

  override def open(path: String, fi: FuseFileInfo): Int = {
    0
  }

  override def read(path: String, buf: Pointer, size: Long, offset: Long, fi: FuseFileInfo): Int = {
    val file = get(path)
    val toRead = Math.min(file.size - offset, size).toInt
    val ret = file.read(toRead,offset)
    buf.put(0,ret,0,ret.length)
    toRead
  }

  override def write(path: String, buf: Pointer, size: Long, offset: Long, fi: FuseFileInfo): Int = {
    0
  }

  override def opendir(path: String, fi: FuseFileInfo): Int = {
    0
  }

  override def readdir(path: String, buf: Pointer, filter: FuseFillDir, offset: Long, fi: FuseFileInfo): Int = {
    val f = try {
      get(path).asInstanceOf[CloudDirectory]
    } catch {
      case FileNonExistent =>
        return -ErrorCodes.ENOENT()
    }
    f.children.foreach(c => filter.apply(buf,c.name,null,0))
    0
  }

  override def init(conn: Pointer): Pointer = {
    super.init(conn)
  }
}
