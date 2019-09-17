package com.jazzpirate.utils

import java.io.InputStream

import com.jazzpirate.gclient.Settings

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

class DownloadBuffer(name:String,maxsize:Long,getStream:Long => (InputStream,Unit=>Unit)) {
  private val streammap : mutable.HashMap[Long,TimedInputStream] = mutable.HashMap.empty
  private val arraymap : mutable.HashMap[Long,(Array[Byte],Long)] = mutable.HashMap.empty

  //private var last_time = System.nanoTime()

  class TimedInputStream(val stream : InputStream,closef:Unit=>Unit) {
    var time = System.nanoTime()
    def touch = time = System.nanoTime()
    private var _closed = false
    def close = if (!_closed) {
      _closed = true
      closef(())
    }
  }

  private def await = {
    while (streammap.values.nonEmpty && arraymap.nonEmpty) {
      Thread.sleep(Settings.timer)
      // garbage collection
      synchronized {
        val now = System.nanoTime()
        arraymap.foreach {
          case (i,(_,t)) if now-t>Settings.timer =>
            arraymap.remove(i)
          case _ =>
        }
        streammap.foreach {
          case (i,is) if now-is.time>Settings.timer =>
            streammap.remove(i)
            is.close
          case _ =>
        }
      }
    }

  }

  private var timer = Future {
    await
  }

  private def touch = {
    //last_time = System.nanoTime()
    if (timer.isCompleted) timer = Future { await }
  }

  private def read(is:InputStream) = {
    var curr = 0
    val arr = new Array[Byte](Settings.chunksize)
    while (curr!= -1 && curr < Settings.chunksize ) {
      val res = is.read(arr,curr,Settings.chunksize - curr)
      if (res == -1) curr = -1 else {
        curr += res
      }
    }
    arr
  }

  private def present(arr:Array[Byte]) : String = new String(arr).replace("\n","\\n").replace("\r","\\r")
    .replace("\t","\\t").replace("\f","\\f").replace("\b","\\b")

  def get(isize:Long,offset:Long):Array[Byte] = {
    val (chunk,fullarr) = synchronized {
      touch
      val ichunk = (offset / Settings.chunksize)
      arraymap.get(ichunk) match {
        // already downloaded; update time stamp and return
        case Some((a,_)) =>
          arraymap(ichunk) = (a,System.nanoTime())
          (ichunk,a)
        // not yet downloaded
        case None =>
          streammap.get(ichunk) match {
              // stream already assigned
            case Some(st) =>
              st.touch
              streammap(ichunk+1) = st
              val arr = read(st.stream)
              arraymap(ichunk) = (arr,st.time)
              //println(name + " New chunk at index " + ichunk + ": " + present(arr))
              (ichunk,arr)
            // ...no assigned input stream
            case None =>
              val now = System.nanoTime()
              val s = getStream(ichunk*Settings.chunksize)
              val stream = new TimedInputStream(s._1,s._2)
              streammap(ichunk) = stream
              streammap(ichunk+1) = stream
              val arr = read(s._1)
              arraymap(ichunk) = (arr,now)
              //println(name + " New chunk at index " + ichunk + ": " + present(arr))
              (ichunk,arr)
          }
      }
    }
    val newoffset = (offset - (chunk*Settings.chunksize)).toInt
    val ret = if (newoffset + isize>Settings.chunksize) {
      val gotten = fullarr.toList.drop(newoffset)
      (gotten ::: get(isize-gotten.length,offset+gotten.length).toList).toArray
    } else fullarr.slice(newoffset.toInt,isize.toInt)
    //println(name + " " + offset + " - " + isize)
    // println(name + " Chunk " + chunk + ": newoffset: " + newoffset + " return: " + present(ret))
    ret
  }
}
