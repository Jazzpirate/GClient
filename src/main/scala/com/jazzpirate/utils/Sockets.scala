package com.jazzpirate.utils

import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import java.net.Socket

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

trait MySocket {
  val socket : Socket
  protected lazy val in = new BufferedReader(new InputStreamReader(socket.getInputStream))
  protected lazy val out =  new PrintWriter(socket.getOutputStream, true)

  def send(s:String) = out.println(s)
  def process(command:String) : Unit
}

abstract class SocketClient(val socket:Socket) extends MySocket {
  private var _killed = false

  def kill = synchronized {
    _killed = true
  }

  Future {
    while(!synchronized{_killed}) {
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
      synchronized{_killed = true}
      null
  }

  def onKill = synchronized{
    _killed = true
  }

  private var _killed = false
  def killed = synchronized{
    _killed
  }

  socket

  Future {
    while(!_killed) {
      val inp = in.readLine()
      if (inp == null) {
        synchronized {
          _killed = false
        }
        onKill
      } else process(inp)
    }
  }
}