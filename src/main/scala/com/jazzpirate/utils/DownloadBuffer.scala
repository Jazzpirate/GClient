package com.jazzpirate.utils

import java.io.InputStream
import java.util.concurrent.{ConcurrentHashMap, ForkJoinPool, TimeUnit, TimeoutException}

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import com.jazzpirate.gclient.Settings

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.util.Random

class DownloadBuffer(name:String,getStream:Long => (InputStream,Unit=>Unit)) {
  private val timing = Settings.timer
  private val chunksize = Settings.settings.getChunksize

  private def log(s:String) = println(Thread.currentThread().getName + " " + name + ": " + s)

  private val arraymap = new ConcurrentLinkedHashMap.Builder[Long,Entry].initialCapacity(10).maximumWeightedCapacity(1000000).build()

  @volatile private var currentStream : Option[TimedInputStream] = None

  private class TimedInputStream(stream : InputStream,closef:Unit=>Unit,start:Long) {
    @volatile var lastAccessed = System.currentTimeMillis()
    def touch = lastAccessed = System.currentTimeMillis()
    def isAlive = !_closed
    @volatile var current = start

    @volatile private var _closed = false
    def close = if (!_closed) {
      _closed = true
      stream.close()
      // if (currentStreams contains this) currentStream = null
      closef(())
    }

   @volatile var index = start
    @volatile var running = false

    def read:List[Byte] = {
      touch
      var curr = 0
      val arr = new Array[Byte](chunksize)
      while (curr!= -1 && curr < chunksize ) {
        val res = stream.read(arr,curr,chunksize - curr)
        if (res == -1) curr = -1 else {
          curr += res
        }
      }
      if (curr == -1) close
      arr.toList
    }

    NewThread {
      touch
      while (!_closed && lastAccessed +timing > System.currentTimeMillis() && currentStream.contains(this)) {
        Thread.sleep(500)
      }
      close
    }
/*
    def run = NewThread {
      touch
      running = true
      while (currentStream.contains(this) && !_closed && lastAccessed + timing > System.currentTimeMillis()) {
        arraymap.get(index) match {
          case e if e != null && e.bytes.isDefined =>
            e.touch
            close
          case _ =>
            val e = Entry(index,this,Some(read))
            arraymap.put(index,e)
            // log("wrote: " + index)
            index += 1
        }

      }
      close
    }
 */
  }

  private case class Entry(index:Long,val source:TimedInputStream,val bytes : Option[List[Byte]]) {
    // @volatile var created = System.nanoTime()
    @volatile var lastAccessed = System.currentTimeMillis()
    def touch = {
      if (source.index - index < 3) source.touch
      lastAccessed = System.nanoTime()
    }

    def isAlive = source.isAlive
  }
/*
  @volatile var streams : List[TimedInputStream] = List({
    val (s,c) = getStream(0)
    new TimedInputStream(s,c,0)
  })
 */


  private def present(arr:Array[Byte]) : String = new String(arr).replace("\n","\\n").replace("\r","\\r")
    .replace("\t","\\t").replace("\f","\\f").replace("\b","\\b")

  def get(size:Long,offset:Long):Array[Byte] = {
    // log("get from " + offset + ": " + size)
    val chunkindex =  offset / chunksize
    val noff = offset - (chunkindex * chunksize)
    val ret = getI(chunkindex,noff.toInt,size).toArray
    // log(offset + ": " + size + " returns " + ret.length + " Bytes")
    // Console.flush()
    ret
  }

  private def getI(chunkindex:Long,inoff:Int,size:Long): List[Byte] = {
    while (!_init) Thread.sleep(10)
    val ch = getEntry(chunkindex)
    // arraymap(sindex) = (ch, now)
    if (inoff + size < chunksize) ch.slice(inoff, inoff + size.toInt)
    else ch.drop(inoff) ::: getI(chunkindex + 1, 0, size + inoff - chunksize)
  }

  // @tailrec
  private def getEntry(index:Long): List[Byte] = scala.concurrent.blocking { synchronized {
    def generate() = {
      val e = Entry(index,null,None)
      arraymap.put(index,e)
      // log("generate " + index)
      currentStream.foreach(_.close)
      val (s,c) = getStream(index*chunksize)
      val source = new TimedInputStream(s,c,0)
      currentStream = Some(source)
      arraymap.put(index,Entry(index,source,Some(source.read)))
      //source.run
      // arraymap.put(index,Entry(index,source,None))
      Thread.sleep(10)
    }
    def find() = {
      (Math.max(index-10,0) to index).findLast {i =>
        val e = arraymap.get(i)
        e != null && e.isAlive
      } match {
        case Some(i) =>
          val s = arraymap.get(i)
          s.touch
          arraymap.put(i+1,Entry(i+1,s.source,Some(s.source.read)))
          // log("read " + (i+1))
          Thread.sleep(10)
        case None =>
          generate()
      }
    }
    arraymap.get(index) match {
      case null =>
        find()
        getEntry(index)
      case entry if entry.bytes.isEmpty =>
        if (entry.source.isAlive) {
          entry.touch
          arraymap.put(index,Entry(index,entry.source,Some(entry.source.read)))
        } else generate()
        // if (!entry.source.running) log("not running")
        getEntry(index)
      case entry =>
        entry.touch
        entry.bytes.get
    }
  } }

  @volatile private var _init = true //false

  /* def init = {
    val (s, c) = getStream(0)
    val source = new TimedInputStream(s, c, 0)
    currentStream = Some(source)
    arraymap.put(0,Entry(0,source,None))
    /* source.run
    while (source.index == 0 && source.isAlive) {
      Thread.sleep(10)
    } */
    _init = true
  }
  arraymap.put(0,Entry(0,null,None))
  init */

}

/*
class DownloadBuffer(name:String,maxsize:Long,getStream:Long => (InputStream,Unit=>Unit)) {
  implicit val ec = ExecutionContext.fromExecutor(new ForkJoinPool(100))
  // private val streammap : mutable.HashMap[Long,TimedInputStream] = mutable.HashMap.empty
  private val arraymap : mutable.HashMap[Long,(List[Byte],TimedInputStream)] = mutable.HashMap.empty

  private var currentStreams : Set[TimedInputStream] = Set.empty

  private lazy val timer_setting = Settings.settings.getTimer
  private lazy val chunksize = Settings.settings.getChunksize

  val self = this

  class TimedInputStream(stream : InputStream,closef:Unit=>Unit,start:Long) {
    var time = System.nanoTime()
    private var index = start
    def getIndex = {index}
    def touch = time = System.nanoTime()
    def touchMaybe(i:Long) = if (index - i < 1000 ) touch
    def closed = _closed

    private var _closed = false
    def close = if (!_closed) {
      _closed = true
      currentStreams -= this
      stream.close()
      // if (currentStreams contains this) currentStream = null
      closef(())
    }

    val thread = Future { // new Thread() {
      // override def run(): Unit = {
        var continue = true
        println(name + " readStream")
        while (continue) {
          if (!_closed && System.nanoTime() - time > timer_setting) {
            try {
              val ret = read
              arraymap(index) = (ret.toList, this)
              index += 1
            } catch {
              case e: java.io.IOException =>
                println("Connection closed prematurely")
                continue = false
                close
              case t: Throwable =>
                t.printStackTrace()
                ???
            }
            // println(name + " current index: " + index)
          } else continue = false
        }
        close
     // }
    }
    // thread.start()

    private def read:Array[Byte] = {
      var curr = 0
      val arr = new Array[Byte](chunksize)
      while (curr!= -1 && curr < chunksize ) {
        val res = stream.read(arr,curr,chunksize - curr)
        if (res == -1) curr = -1 else {
          curr += res
        }
      }
      if (curr == -1) close
      arr
    }

  }

  private def await = {
    while (/* streammap.values.nonEmpty && */ arraymap.nonEmpty) {
      Thread.sleep(Settings.timer)
      // garbage collection
      synchronized {
        val now = System.nanoTime()
        arraymap.foreach {
          case (i,(_,t)) if now-t.time > 10*Settings.timer =>
            arraymap.remove(i)
          case _ =>
        }
        /* streammap.foreach {
          case (i,is) if now-is.time>Settings.timer =>
            streammap.remove(i)
            is.close
          case _ =>
        } */
      }
    }

  }

  private var timer = Future{ // new Thread() {
    // override def run(): Unit =
      await
  }
  // timer.start()

  private def touch = {
    // last_time = System.nanoTime()
    // if (!timer.isAlive) timer.run()
    if (timer.isCompleted) timer = Future { await }
  }

  private def present(arr:Array[Byte]) : String = new String(arr).replace("\n","\\n").replace("\r","\\r")
    .replace("\t","\\t").replace("\f","\\f").replace("\b","\\b")

  def get(isize:Long,offset:Long):Array[Byte] = {
    touch
    val chunk = getI(isize,offset)
    val chind = offset / chunksize
    println(name + " Chunk " + chind + ": subindex: " + (offset - (chind*chunksize)) + ": return: " + present(chunk))
    chunk
  }

  private def getI(isize:Long,offset:Long):Array[Byte] = {
    val cs = currentStreams
      val sindex = offset / chunksize
      val eindex = (offset + isize) / chunksize
      if ((sindex to eindex).forall(arraymap.isDefinedAt)) {
        getExistent(sindex, (offset - (sindex * chunksize)).toInt, isize, System.nanoTime()).toArray
      } else {
        val ns = cs.find(s => !s.closed && s.getIndex <= sindex && sindex - s.getIndex < 100)
        if (ns.isDefined && !ns.get.closed) {
          do {
            Thread.sleep(10)
          } while (!ns.get.closed && ns.get.getIndex - eindex >= 0)
          getI(isize, offset)
        } else {
          println(name + " new stream at " + sindex)
          val (is, cl) = getStream(sindex * chunksize)
          val nstream = new TimedInputStream(is, cl, sindex)
          currentStreams += nstream
          getI(isize, offset)
        }
      }
  }

  private def getExistent(sindex:Long,offset:Int,size:Long,now:Long):List[Byte] = {
    val (ch, tis) = arraymap(sindex)
    tis.touch
    // arraymap(sindex) = (ch, now)
    if (offset + size < chunksize) ch.slice(offset, size.toInt)
    else ch.drop(offset) ::: getExistent(sindex + 1, 0, size + offset - chunksize,now)
  }
/*
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
  } */
}
 */