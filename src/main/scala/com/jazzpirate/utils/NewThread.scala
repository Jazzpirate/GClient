package com.jazzpirate.utils

import java.util.concurrent.ForkJoinPool

import scala.concurrent.{ExecutionContext, Future}


object NewThread {
  implicit val ec = ExecutionContext.fromExecutor(new ForkJoinPool(500))
  def apply[A](a : => Unit) = /* {
    val th = new Thread() {
      override def run(): Unit = a
    }
    th.start()
    th
  }  // */ Future(a)
}
