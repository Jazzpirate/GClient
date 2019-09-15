package com.jazzpirate.gclient.service

import java.net.ServerSocket

import com.jazzpirate.gclient.Settings
import com.jazzpirate.utils.{ExceptionHandler, SocketClient}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

object Service {
  lazy val socket = new ServerSocket(Settings.settings.getServicePort)
  private var clients : List[Client] = Nil

  private def listenThread = Future {
    while(true) {
      val newclient = socket.accept()
      synchronized {
        clients ::= new Client(newclient)
      }
    }
  }

  def main(args: Array[String]) = try {
    listenThread
  } finally {
    clients.foreach(_.kill)
  }

  ExceptionHandler.registerExceptionHandler
}
