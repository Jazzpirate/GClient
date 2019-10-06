package com.jazzpirate.utils

import java.io.{FileInputStream, FileOutputStream}

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import com.jazzpirate.gclient.{AbstractSettings, Settings}
import com.jazzpirate.gclient.hosts.Account
import info.kwarc.mmt.api.utils.{File, JSONInt, JSONObject, JSONString}

abstract class Upload(val path:List[String]) {
  @volatile protected var _size : Long = 0

  def setSize(size:Long) = _size = size
  def getSize = _size
  def addChunk(offset:Long,chunk:Array[Byte]) : Unit
  @volatile protected var _current : Long = 0
  def done = _current == _size

  def finish : Unit

  @volatile var curr_upload : Long = 0
  def getNext(off : Option[Long] = None) : Array[Byte]
}

class UploadBuffer(acc:Account) {
  val folder = Settings.settingsFolder / acc.account_name
  if (!folder.exists()) folder.mkdirs()
  val settings_file = folder / "upload.json"
  if (!settings_file.exists()) File.write(settings_file,"{}")
  private object MySettings extends AbstractSettings(settings_file) {
    def addFile(path : List[String]) = {
      val map = (JSONString(path.mkString("/")),JSONObject()) :: getJson.map
      File.write(settings_file, JSONObject(map).toString)
      update("",null)
    }
    def getFile(path: List[String]) = getJson(path.mkString("/")).map(_.asInstanceOf[JSONObject])
  }

  val chunk_folder = folder / "upload"
  if (!chunk_folder.exists()) chunk_folder.mkdirs()

  class UploadEntry(path:List[String]) extends Upload(path) {
    def addChunk(offset:Long,chunk:Array[Byte]) : Unit = scala.concurrent.blocking { synchronized {
      val file = chunk_folder / (path.hashCode() + offset.hashCode()).toString
      val os = new FileOutputStream(file.toJava)
      os.write(chunk)
      os.close()

      val no = JSONObject(("filename",JSONString(file.name.toString)),("size",JSONInt(chunk.length)))
      _chunks ::= (offset.toString,no)
      _current += chunk.length
      if (done) {
        MySettings.update(path.mkString("/"),JSONObject(_chunks :_*))
        acc.upload(this)
      }
    } }

    @volatile private var _chunks : List[(String,JSONObject)] = Nil

    def finish = {
      val curr = MySettings.getFile(path).get
      MySettings.update(path.mkString("/"),null)
      files.remove(path)
    }

    def getNext(off : Option[Long] = None) : Array[Byte] = {
      if (off.isDefined) curr_upload = off.get

      val oe = MySettings.getFile(path).getOrElse {
        ???
      }
      val obj = try { oe(curr_upload.toString).get.asInstanceOf[JSONObject] } catch {
        case t : Throwable =>
          t.printStackTrace()
          ???
      }
      val f = chunk_folder / obj.getAsString("filename")
      val fs = new FileInputStream(f.toJava)
      val bs = new Array[Byte](obj.getAsInt("size").toInt)
      fs.read(bs)
      fs.close()
      f.delete()
      val ne = JSONObject(oe.map.filter(_._1 != JSONString(curr_upload.toString)))
      curr_upload += bs.length
      MySettings.update(path.mkString("/"),ne)
      bs
    }
  }

  def init(path:List[String],size:Long) = scala.concurrent.blocking {
    val uf = new UploadEntry(path)
    uf.setSize(size)
    files.put(path,uf)
    // MySettings.update(path.mkString("/"),JSONObject())
  }

  def addChunk(path:List[String],offset:Long,chunk:Array[Byte]) = {
    files.get(path).addChunk(offset,chunk)
  }

  private var files = new ConcurrentLinkedHashMap.Builder[List[String],UploadEntry].initialCapacity(10).maximumWeightedCapacity(1000000).build()

  MySettings.getJson.map.reverse foreach {
    case (JSONString(f),o) =>
      val path = f.split('/').toList
      val entry = new UploadEntry(path)
      files.put(path,entry)
      acc.upload(entry)
  }

  import scala.jdk.CollectionConverters._

  // def getNext : Option[Upload] = files.values().asScala.find(_.done)
}
