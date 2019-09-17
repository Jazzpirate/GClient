package com.jazzpirate.gclient.hosts

import com.jazzpirate.gclient.ui.NewAccount
import javax.swing.{JPanel, JTextField}

abstract class Host {
  val id :String
  val name:String
  def getAddAccountPanel(parent:NewAccount) :JPanel
  def getAccount(account_name:String):Account
}

abstract class Account(val host:Host) {

  def account_name : String
  def total_space: Long
  def used_space:Long

  def root : CloudDirectory

  def getFile(path:String*):CloudFile
  def createFile(path:String*):CloudFile
  def rename(newname:List[String],path:String*):CloudFile
  def delete(path : String*) : Unit
  def write(off:Long,content:Array[Byte],path:String*) : Long
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
