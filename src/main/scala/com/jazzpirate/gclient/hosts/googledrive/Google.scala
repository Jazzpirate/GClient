package com.jazzpirate.gclient.hosts.googledrive

import java.io.{InputStreamReader, OutputStream}
import java.net.{HttpURLConnection, URL}

import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http.{ByteArrayContent, GenericUrl, HttpContent}
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.StartPageToken
import com.jazzpirate.gclient.{AbstractSettings, Settings}
import com.jazzpirate.gclient.Settings.settingsFolder
import com.jazzpirate.gclient.fuse.FileNonExistent
import com.jazzpirate.gclient.hosts.{Account, CloudDirectory, CloudFile, Host}
import com.jazzpirate.gclient.ui.NewAccount
import com.jazzpirate.utils.{DownloadBuffer, NewThread, NoInternet, Upload}
import info.kwarc.mmt.api.utils.{File, OSystem, URI}
import javax.net.ssl.SSLHandshakeException
import javax.swing.{JPanel, JTextField}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object Google extends Host {
  val credentials_path = "google_credentials.json"
  val application_name = "GClient"
  val scopes = List(DriveScopes.DRIVE)
  val id = "gdrive"
  val name = "Google Drive/Photos"
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

  private val accounts = new mutable.HashMap[String,Account]()

  override def getAddAccountPanel(parent:NewAccount): JPanel = (new AddAccountForm(parent)).addpanel
  def getAccount(name:String) = synchronized { accounts.getOrElseUpdate(name,new GDrive(name)) }

  lazy val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

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
  private lazy val token = Google.getCredentials(account_name)

  lazy val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
  lazy val service = new Drive.Builder(httpTransport,Google.jsonfac,token).setApplicationName(Google.application_name).build()

  class GoogleFile(val file:model.File) extends CloudFile {
    // var last_time = System.nanoTime()
    protected def touch = {} // last_time = System.nanoTime()
    def name = {touch ; file.getName}
    def id = {touch ; file.getId}
    val account = this_account
    def size = {touch; file.getSize}


    lazy val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    lazy val service = new Drive.Builder(httpTransport,Google.jsonfac,token).setApplicationName(Google.application_name).build()

    private lazy val reader = new DownloadBuffer(name,{offset =>
      val request = service.getRequestFactory.buildGetRequest(new GenericUrl(download_url.toString))
      request.getHeaders.setRange("bytes="+offset+"-"+size)
      val response = request.execute()
      (response.getContent,{_ => response.disconnect()})
    })

    def read(isize:Long,offset:Long):Array[Byte] = {
      touch
      reader.get(isize,offset)
    }

    lazy val download_url = ((URI.https colon "www.googleapis.com") / "drive" / "v3" / "files" / id) ? "alt=media"
    // lazy val upload_url = ((URI.https colon "www.googleapis.com") / "upload" / "drive" / "v3" / "files" / id) ? "alt=media"
    // var upload_url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable"
    // private var up_offset = 0
    // private var buffer = 0
    def write(off:Long,size:Long,chunk:Array[Byte],total:Long): Long = {
      val request = service.files().update(id,new model.File,new ByteArrayContent(null,chunk,off.toInt,size.toInt))
        .set("uploadType","resumable")
      val response = request.execute()
      GFile.remove(this.file)
      size

      /*
      if (upload_url.endsWith("resumable")) {
        val arr = file.toString.toCharArray.map(_.toByte)
        val request = service.getRequestFactory.buildPostRequest(new GenericUrl(upload_url),new HttpContent {
          override def getLength: Long = arr.length
          override def getType: String = "application/json; charset=UTF-8"
          override def retrySupported(): Boolean = false
          override def writeTo(out: OutputStream): Unit = out.write(arr)
        })
        upload_url = request.execute().getHeaders.getLocation
      }
       */


/*
      val connection = new URL(upload_url).openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("PUT")
      connection.setDoOutput(true)
      connection.setConnectTimeout(10000)

      //connection.setRequestProperty("Authorization", "Bearer " + Google.getCredentials(this.name))
      connection.setRequestProperty("Content-Type","application/octet-stream")
      connection.setRequestProperty("Content-Length",size.toString)
      connection.setRequestProperty("Content-Range","bytes " + off + "-" + (off+size-1))
      val stream = connection.getOutputStream
      stream.write(chunk)
      stream.close()
      connection.connect()
      if (connection.getResponseCode == 308) {
        size
      } else {
        ???
      }
*/
      /*
      val request = service.getRequestFactory.buildPutRequest(new GenericUrl(upload_url),new HttpContent {
        override def getLength: Long = size

        override def getType: String = "application/octet-stream"

        override def retrySupported(): Boolean = false

        override def writeTo(out: OutputStream): Unit = out.write(chunk,off.toInt,size.toInt)
      })
      request.getHeaders.setRange("bytes=" + off + "-" + size)
      val response = request.execute()
      upload_url = response.getHeaders.getLocation
      response.getHeaders.getContentLength
       */
    }
  }

  def upload(ul: Upload): Unit = Uploader.upload(ul)
  object Uploader {

    class GoogleUpload(val ul: Upload, init_url: String) {
      var upload_url = init_url

      def path = ul.path

      def size = ul.getSize

      def getNext = ul.getNext(None)
    }

    private val upload_queue = new mutable.Queue[GoogleUpload]

    def upload(ul: Upload): Unit = scala.concurrent.blocking { synchronized {
      // TODO
      println("Queued: " + ul.path.mkString("/"))
      if (upload_queue.exists(_.path == ul.path)) {
        upload_queue.remove(upload_queue.indexWhere(_.path == ul.path))
      }
      upload_queue.addOne(new GoogleUpload(ul, "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable"))
    } }

    NewThread {
      while (true)  try {
        if (upload_queue.isEmpty) Thread.sleep(1000) else scala.concurrent.blocking { synchronized {
          val next = upload_queue.front
          val parent = getFile(next.path.init: _*)
          if (next.upload_url.endsWith("resumable")) {
            println(this.hashCode() +  " Uploading " + next.path.mkString("/"))
            try {
              getFile(next.path: _*)
              delete(next.path: _*)
            } catch {
              case FileNonExistent =>
            }
            val request = service.getRequestFactory.buildPostRequest(new GenericUrl(next.upload_url), new HttpContent {
              val string = "{\"name\": \"" + next.path.last + "\"" + ", \"parents\":[\"" + parent.id + "\"]}"
              val str: Array[Byte] = string.toCharArray.map(_.toByte)

              override def getLength: Long = str.length

              override def getType: String = "application/json; charset=UTF-8"

              override def retrySupported(): Boolean = false

              override def writeTo(out: OutputStream): Unit = out.write(str)
            })
            request.getHeaders.set("X-Upload-Content-Length", next.size)
            val response = try {
              request.execute()
            } catch {
              case t: Throwable =>
                t.printStackTrace()
                throw t
            }
            next.upload_url = response.getHeaders.getLocation
          } else {
            val off = next.ul.curr_upload.toInt
            // val array = next.getNext
            println(this.hashCode() + " Writing to " + next.path.mkString("/"))
            val request = service.getRequestFactory.buildPutRequest(new GenericUrl(next.upload_url), new HttpContent {
              override def getLength: Long = next.size // array.length

              override def getType: String = "application/octet-stream"

              override def retrySupported(): Boolean = true

              override def writeTo(out: OutputStream): Unit = {
                while (next.ul.curr_upload != next.size) {
                  val noff = next.ul.curr_upload.toInt
                  val array = next.getNext
                  out.write(array, off, array.length)
                }
              }
            })
            request.getHeaders.setRange("bytes=" + off + "-" + next.size)
            val response = try {
              request.execute()
            } catch {
              case t: Throwable =>
                t.printStackTrace()
                throw t
            }
            next.upload_url = response.getHeaders.getLocation
            // response.getHeaders.getContentLength
            if (next.ul.curr_upload == next.size) {
              upload_queue.remove(upload_queue.indexOf(next))
              next.ul.finish
              Thread.sleep(1000)
              println("Finishing " + next.path.mkString("/"))
              GFile.remove(getFile(next.path: _*).file)
              GFile.remove(parent.file)
            }
          }
        } }
      } catch {
        case t: Throwable =>
          t.printStackTrace()
          ???
      }
    }

  }

  class GoogleDirectory(val dir:model.File) extends GoogleFile(dir) with CloudDirectory {
    def children = GFile.getChildren(this)
    override lazy val size = 0
    override def touch = {}
  }
  private object GFile {
    private lazy val _mydrive = service.files().get(rootID).setFields("name,id,properties").execute()
    def myDrive = {
      filemap.getOrElseUpdate(_mydrive, {
        new GoogleDirectory(_mydrive) {
          override def name: String = "MyDrive"
        }
      }).asInstanceOf[GoogleDirectory]
    }
    def getChildren(dir:GoogleDirectory) = { childrenmap.getOrElseUpdate(dir,{
      browseStd(dir)
    }) }
    def remove(key:model.File):Unit = {
      filemap.remove(key) match {
        case Some(dir:GoogleDirectory) => childrenmap.remove(dir)
        case Some(f) => childrenmap.find{ case (d,nf) => nf contains f }.foreach(p => remove(p._1.file))
        case _ =>
      } }
    def apply(file:model.File):GoogleFile = {
      filemap.getOrElseUpdate(file,{
        file.getMimeType match {
          case "application/vnd.google-apps.folder" => new GoogleDirectory(file)
          case _ =>
            new GoogleFile(file)
        }
      })
      /*
      filemap.get(file) match {
        case Some(d:GoogleDirectory) =>
        case Some(f) if System.nanoTime() - f.last_time < Settings.settings.getTimer =>
          return f
        case _ =>
      }
      val f = file.getMimeType match {
        case "application/vnd.google-apps.folder" => new GoogleDirectory(file)
        case _ => new GoogleFile(file)
      }
      filemap(file) = f
      f
      */
    }

    private val filemap : mutable.HashMap[model.File,GoogleFile] = mutable.HashMap.empty
    private val childrenmap : mutable.HashMap[GoogleDirectory,List[GoogleFile]] = mutable.HashMap.empty

    import scala.concurrent.ExecutionContext.Implicits._
    private var startPageToken : String = _
    NewThread {
      startPageToken = service.changes().getStartPageToken.execute().getStartPageToken
      while(startPageToken != null) {
        Thread.sleep(2 * Settings.timer)
        val changes = service.changes().list(startPageToken).execute()
        val files = changes.getChanges.asScala.toList.map(_.getFileId)
        if (files.nonEmpty) {
          remove(_mydrive)
          filemap.toList.foreach {
          case (k,d:GoogleDirectory) if d.children.exists(files contains _.id) =>
            remove(k)
          case (k,f) if files contains f.id =>
            remove(k)
          case _ =>
        }
          startPageToken = changes.getNewStartPageToken
        }
      }
      throw NoInternet
    }
  }


  lazy val (user_email,rootID,total_space) = { try {
    val about = service.about().get().setFields("user(displayName,emailAddress), storageQuota(limit)").execute()
    val id = service.files().get("root").setFields("id").execute().getId
    val limit : Long = if (about.getStorageQuota == null || about.getStorageQuota.getLimit == null) 0 else about.getStorageQuota.getLimit
    // val used: Long = about.getStorageQuota.getUsage
    (about.getUser.getEmailAddress,id,limit)
  } catch {
    case t:java.net.UnknownHostException =>
      throw NoInternet
  } }

  override def used_space: Long = {
    service.about().get().setFields("storageQuota(usage)").execute().getStorageQuota.getUsage
  }

  lazy val shared : GoogleDirectory = new GoogleDirectory(null) {
    override val id: String = ""
    override val name: String = "Shared"
    override def children: List[GoogleFile] = getFiles(_.setQ("sharedWithMe=true"))
  }

  // private var _trash = trash
  private lazy val trash : GoogleDirectory = new GoogleDirectory(null) {
    override val id: String = ""
    override val name = ".Trash"
    override def children: List[GoogleFile] = getFiles(_.setQ("trashed=true"))
  }

  lazy val root = new GoogleDirectory(null) {
      override val id: String = ""
      override lazy val name = user_email
      override def children: List[GoogleFile] = List(trash,shared,GFile.myDrive)
  }

  private def getList = service.files().list().setPageSize(100)
    .setCorpora("allDrives").setIncludeItemsFromAllDrives(true).setSupportsAllDrives(true).setFields("nextPageToken, files(id,name,mimeType,size,trashed,kind,parents)")

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
    val curr = list.execute()
    val drvs = curr.getDrives.asScala.toList.map(_.getId)
    rootID :: drvs
  }

  private def browseStd(dir : GoogleDirectory) = {
    getFiles(_.setQ("'"+dir.id+"' in parents")).filter(!_.file.getTrashed)
  }

  override def createFile(path: String*): CloudFile = try {
    getFile(path.init:_*) match {
      case d:GoogleDirectory =>
        val f = new model.File
        f.setName(path.last)
        f.setParents(List(d.file.getId).asJava)
        val rf = service.files().create(f).setFields("id,name,parents").execute()
        GFile.remove(d.file)
        GFile(rf)
      case _ =>
        ???
    }
  } catch {
    case t:Throwable =>
      ???
  }

  override def rename(newName:List[String],path:String*) :GoogleFile = try {
    try {
      val orig = getFile(newName:_*)
      delete(newName:_*)
    } catch {
      case FileNonExistent =>
      case t:Throwable =>
        t.printStackTrace()
        ???
    }
    val of = getFile(path:_*).file
    val oldparent = getFile(path.init:_*)
    val newparent = getFile(newName.init:_*)
    GFile.remove(oldparent.file)
    GFile.remove(of)
    val nf = new model.File
    nf.setName(newName.last)
    //of.getParents.remove(of.getParents.asScala.indexOf(oldparent.id))
    //of.getParents.add(newparent.id)
    //of.setName(newName.last)
    val ret = service.files().update(of.getId,nf).setRemoveParents(oldparent.id).setAddParents(newparent.id).execute()
    GFile(ret)
  } catch {
    case t:Throwable =>
      t.printStackTrace()
      ???
  }

  override def delete(path: String*): Unit = try {
    val of = getFile(path:_*).file
    val oldparent = getFile(path.init:_*)
    GFile.remove(oldparent.file)
    GFile.remove(of)
    val nf = new model.File().setTrashed(true)
    //of.getParents.remove(of.getParents.asScala.indexOf(oldparent.id))
    //of.getParents.add(newparent.id)
    //of.setName(newName.last)
    val ret = service.files().update(of.getId,nf).execute()
    GFile(ret)
  } catch {
    case t:Throwable =>
      t.printStackTrace()
      ???
  }

  override def write(off:Long,content: Array[Byte], path: String*): Long = {
    val file = getFile(path:_*)
    file.write(off,content.length,content,0)
    // ???
  }

  override def getFile(path: String*): GoogleFile = try {
    upload_buffer
    path.toList match {
      case Nil =>
        root
      case "" :: rest =>
        getFile(rest: _*)
      case "Shared" :: rest =>
        getFileIt(shared, rest)
      case "MyDrive" :: rest =>
        getFileIt(GFile.myDrive, rest)
      case ".Trash" :: rest =>
        getFileIt(trash, rest)
      case _ =>
        throw FileNonExistent
    }
  } catch {
    case t:Throwable =>
      t.printStackTrace()
      throw t
  }

  private def getFileIt(curr:GoogleDirectory,rest:List[String]) : GoogleFile = try {
    if (rest.isEmpty) curr else {
      val c = curr.children.find(_.name==rest.head)
      c match {
        case Some(d:GoogleDirectory) => getFileIt(d,rest.tail)
        case Some(f) if rest.tail.isEmpty =>
          f
        case _ =>
          throw FileNonExistent
      }
    }
  } catch {
    case FileNonExistent => throw FileNonExistent
      throw NoInternet
    case t:Throwable =>
      t.printStackTrace()
      throw t
  }

}

object TestDrive extends GDrive("test")