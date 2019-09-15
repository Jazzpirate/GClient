package com.jazzpirate.gclient

import com.jazzpirate.gclient.hosts.Host
import com.jazzpirate.gclient.hosts.googledrive.Google
import info.kwarc.mmt.api.utils.{File, JSON, JSONObject, JSONString, OS, OSystem}

object Settings {
  lazy val settingsFolder: File = {
    val sf = OS.settingsFolder / ".gclient"
    if (!sf.exists()) sf.mkdirs()
    sf
  }
  lazy val settings: Settings = {
    val sf = settingsFolder / "settings.json"
    if (!sf.exists()) {
      val str = OSystem.getResourceAsString("/default.json")
      File.write(sf,str)
    }
    // JSON.parse(File.read(sf))
    new Settings(sf)
  }
  // val mountFolder = settingsFolder / "mount"
  def timer: Int = settings.getTimer
  def chunksize: Int = settings.getChunksize

  lazy val hosts: List[Host] = List(Google)
}

abstract class AbstractSettings(settings_file:File) {
  protected def getJson = JSON.parse(File.read(settings_file)).asInstanceOf[JSONObject]
  protected def update(key:String,value:JSON) = synchronized {
    val nmap = getJson.map.map {
      case (JSONString(`key`),_) => (JSONString(key),value)
      case p => p
    }
    File.write(settings_file,JSONObject(nmap).toString)
  }
}

class Settings(settings_file:File) extends AbstractSettings(settings_file) {

  def getTimer: Int = getJson.getAsInt("timer").toInt
  def getChunksize: Int = getJson.getAsInt("chunksize").toInt
  def getServicePort: Int = getJson.getAsInt("service_port").toInt
}