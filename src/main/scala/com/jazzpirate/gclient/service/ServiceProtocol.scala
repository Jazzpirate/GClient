package com.jazzpirate.gclient.service

import java.net.Socket

import com.jazzpirate.utils.{SocketClient, SocketServer}

class Server(port:Int) extends SocketServer(port) {
  override def process(s: String): Unit = s match {
    case "killed" =>
      onKill
  }

  def killServer = send("kill")
}
class Client(socket:Socket) extends SocketClient(socket) {
  override def process(s: String): Unit = s match {
    case "kill" =>
      Service.finish
      System.exit(2)
  }
  def onKilled = send("killed")
}