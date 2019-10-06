package com.jazzpirate.utils

import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import java.net.Socket

trait MySocket {
  val socket : Socket
  protected lazy val in = new BufferedReader(new InputStreamReader(socket.getInputStream))
  protected lazy val out =  new PrintWriter(socket.getOutputStream, true)

  def send(s:String) = out.println(s)
  def process(command:String) : Unit
}

abstract class SocketClient(val socket:Socket) extends MySocket {
  @volatile private var _killed = false

  def kill = {
    _killed = true
  }

  NewThread {
    while(!_killed) {
      val inp = in.readLine()
      if (inp == null) kill else process(inp)
    }
  }
}

abstract class SocketServer(port:Int) extends MySocket {
  lazy val socket = try {
    new Socket("localhost",port)
  } catch {
    case _:java.net.ConnectException =>
      _killed = true
      null
  }

  def onKill = {
    _killed = true
  }

  private var _killed = false
  def killed = {
    _killed
  }

  socket

  NewThread {
    while(!_killed) {
      val inp = in.readLine()
      if (inp == null) {
        {
          _killed = false
        }
        onKill
      } else process(inp)
    }
  }
}