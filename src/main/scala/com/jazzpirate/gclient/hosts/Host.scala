package com.jazzpirate.gclient.hosts

import javax.swing.JPanel

abstract class Host {
  val id :String
  val name:String
  def getAddAccountPanel :JPanel
}

abstract class Account(val host:Host) {

  def user_name : String
  def total_space: Long
  def used_space:Long

  def root : CloudDirectory

  def getFile(path:String*):CloudFile
}

abstract class CloudFile {
  def name: String
  def account:Account
  def size:Long

  def read(size:Long,offset:Long):Array[Byte]
}

trait CloudDirectory extends CloudFile {
  def children: List[CloudFile]
}
