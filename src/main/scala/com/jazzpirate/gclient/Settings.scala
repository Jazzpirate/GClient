package com.jazzpirate.gclient

import com.jazzpirate.gclient.hosts.{Account, Host}
import com.jazzpirate.gclient.hosts.googledrive.Google
import info.kwarc.mmt.api.utils.{File, JSON, JSONArray, JSONObject, JSONString, OS, OSystem}

object Settings {
  lazy val settingsFolder: File = {
    val sf = OS.settingsFolder / ".gclient"
    if (!sf.exists()) sf.mkdirs()
    sf
  }
  lazy val settings: Settings = {
    val sf = settingsFolder / "settings.json"
    if (!sf.exists()) {
      val str = OSystem.getResourceAsString("/default_settings.json")
      File.write(sf,str)
    }
    // JSON.parse(File.read(sf))
    new Settings(sf)
  }
  // val mountFolder = settingsFolder / "mount"
  def timer: Long = settings.getTimer
  def chunksize: Int = settings.getChunksize

  lazy val hosts: List[Host] = List(Google)
}

abstract class AbstractSettings(settings_file:File) {
  val sf = settings_file
  if (!sf.exists()) sf.mkdirs()

  @volatile private var _json = JSON.parse(File.read(settings_file)).asInstanceOf[JSONObject]
  def getJson = _json
  def update(key:String,value:JSON) = {
    if (key != "" && value != null) {
      var existed = false
      val nmap1 = getJson.map.map {
        case (JSONString(`key`), _) =>
          existed = true
          (JSONString(key), value)
        case p => p
      }
      val nmap = if (existed) nmap1 else (JSONString(key),value) :: nmap1
      _json = JSONObject(nmap)
      File.write(settings_file, _json.toString)
    } else if (value == null) {
      _json = JSONObject(getJson.map.filter(_._1 != JSONString(key)))
      File.write(settings_file, _json.toString)
    }
    else _json = JSON.parse(File.read(settings_file)).asInstanceOf[JSONObject]
  }
}

class Settings(settings_file:File) extends AbstractSettings(settings_file) {
  def getTimer: Long = getJson.getAsInt("timer").toInt
  def getChunksize: Int = getJson.getAsInt("chunksize").toInt
  def getServicePort: Int = getJson.getAsInt("service_port").toInt
  def getAccounts : List[Account] = getJson.getAsList(classOf[JSONObject],"accounts").map {jo =>
    val name = jo.getAsString("name")
    val host = Settings.hosts.find(_.id == jo.getAsString("host")).getOrElse{
      ???
    }
    host.getAccount(name)
  }
  def addAccount(name:String,host:Host) = {
    val all = getJson.getAsList(classOf[JSONObject],"accounts")
    val nl =JSONObject(("name",JSONString(name)),("host",JSONString(host.id))) :: all
    update("accounts",JSONArray(nl:_*))
  }
  def addSync(s:Sync) = s match {
    case Mount(acc,file,cpath) =>
      val all = getJson.getAsList(classOf[JSONObject],"mounts")
      val nall = JSONObject(
        ("account",JSONString(acc)),
        ("local_path",JSONString(file.toString)),
        ("cloud_path",JSONString(cpath.mkString("/")))) :: all
      update("mounts",JSONArray(nall:_*))
    case SyncedFolder(acc,file,cpath) =>
      val all = getJson.getAsList(classOf[JSONObject],"syncs")
      val nall = JSONObject(
        ("account",JSONString(acc)),
        ("local_path",JSONString(file.toString)),
        ("cloud_path",JSONString(cpath.mkString("/")))) :: all
      update("syncs",JSONArray(nall:_*))
  }
  def removeSync(s:Sync) = s match {
    case Mount(acc,file,cpath) =>
      val all = getJson.getAsList(classOf[JSONObject],"mounts")
      val self = all.find{o =>
        (o("account") contains JSONString(acc)) && (o("local_path") contains JSONString(file.toString)) &&
          (o("cloud_path") contains JSONString(cpath.mkString("/")))
      }
      val nall = all.filter(!self.contains(_))
      update("mounts",JSONArray(nall:_*))
    case SyncedFolder(acc,file,cpath) =>
      val all = getJson.getAsList(classOf[JSONObject],"syncs")
      val self = all.find{o =>
        (o("account") contains JSONString(acc)) && (o("local_path") contains JSONString(file.toString)) &&
          (o("cloud_path") contains JSONString(cpath.mkString("/")))
      }
      val nall = all.filter(!self.contains(_))
      update("syncs",JSONArray(nall:_*))
  }

  def getMounts : List[Mount] = {
    val all = getJson.getAsList(classOf[JSONObject],"mounts")
    all.map { mount =>
      val acc = mount.getAsString("account")
      val local_path = mount.getAsString("local_path")
      val cloud_path = mount.getAsString("cloud_path").split('/')
      Mount(acc,File(local_path),cloud_path.toList)
    }
  }
  def getSyncs : List[SyncedFolder] = {
    val all = getJson.getAsList(classOf[JSONObject],"syncs")
    all.map { mount =>
      val acc = mount.getAsString("account")
      val local_path = mount.getAsString("local_path")
      val cloud_path = mount.getAsString("local_path").split('/')
      SyncedFolder(acc,File(local_path),cloud_path.toList)
    }
  }
}

trait Sync
case class Mount(account_id:String,local_path:File,cloud_path:List[String]) extends Sync
case class SyncedFolder(account_id:String,local_path:File,cloud_path:List[String]) extends Sync