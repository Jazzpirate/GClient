package com.jazzpirate.gclient.hosts.googledrive

import java.io.InputStreamReader

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.GenericUrl
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.DriveScopes
import com.jazzpirate.gclient.{AbstractSettings, Settings}
import com.jazzpirate.gclient.Settings.settingsFolder
import com.jazzpirate.gclient.fuse.FileNonExistent
import com.jazzpirate.gclient.hosts.{Account, CloudDirectory, CloudFile, Host}
import com.jazzpirate.gclient.ui.NewAccount
import com.jazzpirate.utils.{DownloadBuffer, NoInternet}
import info.kwarc.mmt.api.utils.{File, OSystem, URI}
import javax.swing.{JPanel, JTextField}

import scala.collection.mutable
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

object Google extends Host {
  val credentials_path = "google_credentials.json"
  val application_name = "GClient"
  val scopes = List(DriveScopes.DRIVE)
  val id = "gdrive"
  val name = "Google Drive/Photos"
  lazy val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
  lazy val jsonfac = JacksonFactory.getDefaultInstance

  lazy val settings: GoogleSettings = {
    val sf = settingsFolder / "gdrive.json"
    if (!sf.exists()) {
      val str = OSystem.getResourceAsString("google_settings.json")
      File.write(sf,str)
    }
    // JSON.parse(File.read(sf))
    new GoogleSettings(sf)
  }

  def token_dir(user:String) = Settings.settingsFolder / user / "tokens"

  override def getAddAccountPanel(parent:NewAccount): JPanel = (new AddAccountForm(parent)).addpanel
  def getAccount(name:String) = new GDrive(name)

  def getCredentials(user:String) = try {
    val cred = OSystem.getResource(Google.credentials_path)
    val clientSecrets = GoogleClientSecrets.load(jsonfac,new InputStreamReader(cred))
    val flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport,jsonfac,clientSecrets:GoogleClientSecrets,scopes.asJava)
      .setDataStoreFactory(new FileDataStoreFactory(token_dir(user))).setAccessType("offline").build()
    val receiver = new LocalServerReceiver.Builder().setPort(Google.settings.getPort).build()
    new AuthorizationCodeInstalledApp(flow,receiver).authorize("user")
  } catch {
    case t:Throwable =>
      println(t)
      ???
  }
}

class GoogleSettings(gfile:File) extends AbstractSettings(gfile) {
  def getPort = getJson.getAsInt("port").toInt
}

class GDrive(val account_name : String) extends Account(Google) {
  import com.google.api.services.drive.{Drive, model}

  private val this_account = this

  class GoogleFile(val file:model.File) extends CloudFile {
    var last_time = System.nanoTime()
    protected def touch = last_time = System.nanoTime()
    def name = {touch ; file.getName}
    def id = {touch ; file.getId}
    val account = this_account
    def size = {touch; file.getSize}
    private lazy val reader = new DownloadBuffer(name,size,{offset =>
      val request = service.getRequestFactory.buildGetRequest(new GenericUrl(url.toString))
      request.getHeaders.setRange("bytes="+offset+"-"+size)
      val response = request.execute()
      (response.getContent,{_ => response.disconnect()})
    })

    def read(isize:Long,offset:Long):Array[Byte] = {
      touch
      reader.get(isize,offset)
    }

    lazy val url = ((URI.https colon "www.googleapis.com") / "drive" / "v3" / "files" / id) ? "alt=media"
  }
  class GoogleDirectory(val dir:model.File) extends GoogleFile(dir) with CloudDirectory {
    def children = {touch; _children}
    private lazy val _children = browseStd(this)
    override lazy val size = 0
  }
  private object GFile {
    def apply(file:model.File) = {
      touch
      filemap.getOrElseUpdate(file,{ file.getMimeType match {
        case "application/vnd.google-apps.folder" => new GoogleDirectory(file)
        case _ => new GoogleFile(file)
      } })
    }

    import scala.concurrent.ExecutionContext.Implicits._

    private def touch = {
      //last_time = System.nanoTime()
      if (timer.isCompleted) timer = Future { await }
    }

    //private var last_time = System.nanoTime()
    private def await = {
      while (filemap.values.nonEmpty) {
        Thread.sleep(Settings.timer)
        // garbage collection
        synchronized {
          val now = System.nanoTime()
          filemap.foreach {
            case (fi,f) if now-f.last_time>Settings.timer =>
              filemap.remove(fi)
            case _ =>
          }
        }
      }

    }

    private var timer = Future {
      await
    }

    private val filemap : mutable.HashMap[model.File,GoogleFile] = mutable.HashMap.empty
  }

  lazy val service = new Drive.Builder(Google.httpTransport,Google.jsonfac,Google.getCredentials(account_name)).setApplicationName(Google.application_name).build()

  lazy val (user_email,rootID,total_space) = try {
    val about = service.about().get().setFields("user(displayName,emailAddress), storageQuota(limit)").execute()
    val id = service.files().get("root").setFields("id").execute().getId
    val limit : Long = if (about.getStorageQuota == null || about.getStorageQuota.getLimit == null) 0 else about.getStorageQuota.getLimit
    // val used: Long = about.getStorageQuota.getUsage
    (about.getUser.getEmailAddress,id,limit)
  } catch {
    case t:java.net.UnknownHostException =>
      throw NoInternet
  }

  override def used_space: Long = {
    service.about().get().setFields("storageQuota(usage)").execute().getStorageQuota.getUsage
  }

  private lazy val shared = new GoogleDirectory(null) {
    override val id: String = ""
    override val name: String = "Shared"
    override lazy val children: List[GoogleFile] = getFiles(_.setQ("sharedWithMe=true"))
  }
  private lazy val myDrive = new GoogleDirectory(service.files().get(rootID).setFields("name,id,properties").execute()) {
    override val name = "MyDrive"
  } //GFile(service.files().get(rootID).execute()).asInstanceOf[GoogleDirectory]
  private def trash = new GoogleDirectory(null) {
    override val id:String = ""
    override val name = ".Trash"
    override lazy val children: List[GoogleFile] = getFiles(_.setQ("trashed=true"))
  }
  lazy val root = new GoogleDirectory(null) {
      override val id: String = ""
      override lazy val name = user_email
      override val children: List[GoogleFile] = List(trash,shared,myDrive)
  }

  private def getList = service.files().list().setPageSize(100)
    .setCorpora("allDrives").setIncludeItemsFromAllDrives(true).setSupportsAllDrives(true).setFields("nextPageToken, files(id,name,mimeType,size,trashed)")

  private def getFiles(command: Drive#Files#List => Drive#Files#List) = {
    val list = command(getList)
    var results : List[GoogleFile] = Nil
    var curr = list.execute()
    curr.getFiles.iterator.asScala.foreach(results ::= GFile(_))
    while (curr.getNextPageToken != null) {
      list.setPageToken(curr.getNextPageToken)
      curr = list.execute()
      curr.getFiles.iterator.asScala.foreach(results ::= GFile(_))
    }
    results
  }

  def getDrives = {
    val list = service.drives.list().setPageSize(100).setFields("nextPageToken, drives(id, name)")
    var curr = list.execute()
    var drvs = curr.getDrives.asScala.toList.map(_.getId)
    rootID :: drvs
  }

  private def browseStd(dir : GoogleDirectory) = {
    getFiles(_.setQ("'"+dir.id+"' in parents")).filter(!_.file.getTrashed)
  }

  override def getFile(path: String*): GoogleFile = path.toList match {
      case Nil =>
        root
      case "" :: rest =>
        getFile(rest: _*)
      case "Shared" :: rest =>
        getFileIt(shared, rest)
      case "MyDrive" :: rest =>
        getFileIt(myDrive, rest)
      case ".Trash" :: rest =>
        getFileIt(trash, rest)
      case _ =>
        throw FileNonExistent
    }

  private def getFileIt(curr:GoogleDirectory,rest:List[String]) : GoogleFile = {
    if (rest.isEmpty) curr else {
      val c = curr.children.find(_.name==rest.head)
      c match {
        case Some(d:GoogleDirectory) => getFileIt(d,rest.tail)
        case Some(f) if rest.tail.isEmpty =>
          f
        case _ =>
          ???
      }
    }
  }
}

object TestDrive extends GDrive("test")