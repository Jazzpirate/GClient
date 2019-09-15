package com.jazzpirate.gclient.ui

import java.awt.Dimension
import java.awt.event.{ActionEvent, ActionListener}

import com.intellij.uiDesigner.core.GridConstraints
import com.jazzpirate.gclient.hosts.Host
import javax.swing.JPanel

class NewAccount(host:Host) extends NewAccountJava {
  btn_back.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = Main.getBack(top_panel)
  })
  scroll_pane.remove(replacepanel)
  val hostpanel = host.getAddAccountPanel(this)
  hostpanel.setVisible(true)
  scroll_pane.setViewportView(hostpanel)
}
