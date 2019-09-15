package com.jazzpirate.gclient.service

import java.net.Socket

import com.jazzpirate.utils.{SocketClient, SocketServer}

class Server(port:Int) extends SocketServer(port) {
  override def process(s: String): Unit = ???

  def killServer = send("kill")
  def onKill = {}
}
class Client(socket:Socket) extends SocketClient(socket) {
  override def process(s: String): Unit = s match {
    case "kill" => System.exit(2)
  }
}