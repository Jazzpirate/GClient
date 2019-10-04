package com.jazzpirate.gclient.ui

import java.awt.{Dimension, Insets}
import java.awt.TrayIcon.MessageType
import java.awt.event.{ActionEvent, ActionListener}
import java.util.concurrent.ForkJoinPool

import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}

import scala.jdk.CollectionConverters._
import com.jazzpirate.gclient.Settings
import com.jazzpirate.gclient.hosts.Host
import com.jazzpirate.gclient.service.{Server, Service}
import com.jazzpirate.utils.{ExceptionHandler, NewThread, NoInternet}
import info.kwarc.mmt.api.utils.RunJavaClass
import javax.swing.{ButtonGroup, JFrame, JOptionPane, JPanel, JRadioButton, WindowConstants}


object Main extends MainJava {
  // implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(100))
  val debug = true

  private var _socket :Server = _
  def socket : Server = {
      if (synchronized {_socket} == null) {
        val ns = newSocket
        synchronized{_socket = ns}
      }
      _socket
  }

  private def runService = {
    if (debug) NewThread { Service.main(Array("await")) }
    else {
      val ret = RunJavaClass("com.jazzpirate.gclient.service.Service", List("await")).start()
      println(ret)
    }
  }

  private def newSocket = {
    var server = new Server(Settings.settings.getServicePort)
    if (server.socket == null) {
      runService
    }
    while (server.socket == null) {
      Thread.sleep(100)
      server = new Server(Settings.settings.getServicePort)
    }
    server
  }

  private var _frame :JFrame = _

  //new Socket("localhost",Settings.settings.getServicePort)
  def main(args: Array[String]) = try {
    args.headOption match {
      case Some("-service") =>
        Service.main(args.tail)
      case _ =>
        _frame = new JFrame("GClient")
        _frame.setSize(600,600)
        init
        _frame.setContentPane(mainPanel)
        _frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
        //_frame.pack()
        _frame.setVisible(true)
    }
    print("")
  }

  val buttongroup = new ButtonGroup

  class HostButton(val host:Host) extends JRadioButton(host.name) {
    addActionListener(al)
  }

  private object al extends ActionListener{
    override def actionPerformed(e: ActionEvent): Unit = e.getSource match {
      case h:HostButton if h.isSelected =>
        btn_add.setEnabled(true)
    }
  }

  def init: Unit = {
    socket
    // reset
    btn_stop.setEnabled(false)
    clientBox.setSelected(false)
    clientBox.setEnabled(true)
    clientBox.addActionListener(new ActionListener {
      override def actionPerformed(actionEvent: ActionEvent): Unit = {
        socket
        init
      }
    })
    hosts_pane.getComponents.foreach {
      case h: HostButton => hosts_pane.remove(h)
      case _ =>
    }
    connected_pane.getComponents.foreach {
      case t if t == no_syncs_connected =>
      case t => connected_pane.remove(t)
    }
    buttongroup.getElements.asScala.foreach(buttongroup.remove)
    no_hosts_available.setVisible(true)
    no_syncs_connected.setVisible(true)
    btn_add.getActionListeners.foreach(btn_add.removeActionListener)
    tabbed.getComponents.foreach {
      case t if t == tab_main =>
      case t => tabbed.remove(t)
    }

    // fill
    if(!socket.killed) {
      clientBox.setSelected(true)
      btn_stop.setEnabled(true)
      clientBox.setEnabled(false)
    }

    val syncs = Settings.settings.getMounts ::: Settings.settings.getSyncs
    if (syncs.nonEmpty) no_syncs_connected.setVisible(false)
    connected_pane.setLayout(new GridLayoutManager(Math.max(syncs.size,1), 1, new Insets(0, 0, 0, 0), -1, -1))
    syncs.zipWithIndex.foreach {case (s,i) =>
      val sf = new SyncForm(s)
      connected_pane.add(sf.panel,new GridConstraints(i, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(150, 20), null, 0, true))
    }

    if (Settings.hosts.nonEmpty) no_hosts_available.setVisible(false)
    Settings.hosts.foreach{h =>
      val b =new HostButton(h)
      hosts_pane.add(b)
      buttongroup.add(b)
      hosts_pane.setViewportView(b)
      b.setVisible(true)
    }
    Settings.settings.getAccounts.foreach { ac =>
      tabbed.addTab(ac.account_name, new AccountForm(ac).main_panel)
    }
    btn_add.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = {
        val host = buttongroup.getElements.asScala.find(_.isSelected) match {
          case Some(h:HostButton) => h.host
          case _ =>
            ???
        }
        tabbed.setVisible(false)
        val hostpanel = new NewAccount(host)
        mainPanel.add(hostpanel.top_panel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false))
        hostpanel.top_panel.setVisible(true)
        //_frame.pack()
      }
    })
  }

  def getBack(self:Option[JPanel]) = {
    self.foreach(mainPanel.remove)
    tabbed.setVisible(true)
    init
    //_frame.pack()
  }

  ExceptionHandler.registerExceptionHandler

  btn_stop.addActionListener(new ActionListener {
    def actionPerformed(e:ActionEvent) = {
      socket.killServer
      Thread.sleep(100)
      init
    }
  })
}
