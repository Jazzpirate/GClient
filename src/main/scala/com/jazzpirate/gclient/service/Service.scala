package com.jazzpirate.gclient.service

import java.net.ServerSocket
import java.util.concurrent.ForkJoinPool

import com.jazzpirate.gclient.fuse.CloudFuse
import com.jazzpirate.gclient.{Mount, Settings, SyncedFolder}
import com.jazzpirate.utils.{ExceptionHandler, NewThread, SocketClient}

object Service {
  // implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(100))
  lazy val socket = new ServerSocket(Settings.settings.getServicePort)
  private var clients : List[Client] = Nil

  private var mounts : List[CloudFuse] = Nil

  private def listenThread = {
    NewThread {
      synchronized {threadRunning = true}
      while(true) {
        val newclient = socket.accept()
        val cl = new Client(newclient)
        synchronized {
          clients ::= cl
        }
      }
    }
  }

  private var threadRunning = false

  def main(args: Array[String]) = try {
    listenThread
    while (!synchronized{threadRunning}) {
      Thread.sleep(100)
    }
    if (args.headOption contains "await") while(synchronized{clients}.isEmpty) {
      Thread.sleep(100)
    }
    Settings.settings.getMounts.foreach {
      case m@Mount(acc_id,local,cloud) =>
        val account = Settings.settings.getAccounts.find(_.account_name==acc_id).getOrElse {
          ???
        }
        val id = m.hashCode()
        val fuse = new CloudFuse(account,cloud,id.toString,local)
        mounts::= fuse
        NewThread { fuse.mountCloud() }
    }
    Settings.settings.getSyncs.foreach {
      case SyncedFolder(acc_id,local,cloud) =>
      // TODO
    }
  } finally {
    finish
  }

  def finish = {
    clients.foreach(_.onKilled)
    clients.foreach(_.kill)
  }

  ExceptionHandler.registerExceptionHandler
}
