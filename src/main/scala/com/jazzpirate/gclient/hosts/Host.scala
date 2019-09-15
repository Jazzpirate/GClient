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
