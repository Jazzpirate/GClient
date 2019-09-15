package com.jazzpirate.utils

import com.jazzpirate.gclient.ui.Main.mainPanel
import javax.swing.JOptionPane

object ExceptionHandler {
  def registerExceptionHandler: Unit = {
    Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler)
    System.setProperty("sun.awt.exception.handler", classOf[ExceptionHandler].getName)
  }
}

class ExceptionHandler extends Thread.UncaughtExceptionHandler {
  override def uncaughtException(t: Thread, e: Throwable): Unit = {
    handle(e)
  }

  def handle(throwable: Throwable): Unit = throwable match {
    case NoInternet =>
      JOptionPane.showMessageDialog(mainPanel,"Can not reach cloud host\nInternet connection required!","Error",0)
      System.exit(0)
    case e:java.security.PrivilegedActionException =>
      handle(e.getException)
    case e:java.util.concurrent.ExecutionException =>
      handle(e.getCause)
    case t:Throwable =>
      JOptionPane.showMessageDialog(mainPanel,t.getMessage + "\n\n" + t.getStackTrace.toList.mkString("\n"),"Error",0)
      System.exit(0)
  }
}

trait GClientError extends Error

object NoInternet extends GClientError