package com.jazzpirate.gclient.ui

import java.awt.event.{ActionEvent, ActionListener}

import com.jazzpirate.gclient.{Mount, Settings, Sync, SyncedFolder}

class SyncForm(sync:Sync) extends SyncFormJava {
  sync match {
    case Mount(account,local_path,cloud_path) =>
      txt_kind.setText("Mount")
      txt_localPath.setText(local_path.toString)
      txt_cloudPath.setText(cloud_path.mkString("/","/",""))
      txt_host.setText(account)
    case SyncedFolder(account,local_path,cloud_path) =>
      txt_kind.setText("Sync")
      txt_localPath.setText(local_path.toString)
      txt_cloudPath.setText(cloud_path.mkString("/","/",""))
      txt_host.setText(account)
  }
  btn_remove.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      Settings.settings.removeSync(sync)
      Main.getBack(None)
    }
  })
}
