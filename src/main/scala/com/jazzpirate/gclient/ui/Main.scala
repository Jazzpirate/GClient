package com.jazzpirate.gclient.ui

import java.awt.Dimension
import java.awt.TrayIcon.MessageType
import java.awt.event.{ActionEvent, ActionListener}

import com.intellij.uiDesigner.core.GridConstraints

import scala.jdk.CollectionConverters._
import com.jazzpirate.gclient.Settings
import com.jazzpirate.gclient.hosts.Host
import com.jazzpirate.gclient.service.{Server, Service}
import com.jazzpirate.utils.NoInternet
import javax.swing.{ButtonGroup, JFrame, JOptionPane, JPanel, JRadioButton, WindowConstants}

object Main extends MainJava {
  private var _socket :Server = _
  def socket = synchronized {
    if (_socket == null || !_socket.killed) _socket = new Server(Settings.settings.getServicePort)
    _socket
  }

  private var _frame :JFrame = _

  //new Socket("localhost",Settings.settings.getServicePort)
  def main(args: Array[String]) = try {
    args.headOption match {
      case Some("-service") =>
        Service.main(args.tail)
      case _ =>
        _frame = new JFrame("GClient")
        init
        _frame.setContentPane(mainPanel)
        _frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
        _frame.pack()
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
    hosts_pane.getComponents.foreach {
      case h: HostButton => hosts_pane.remove(h)
      case _ =>
    }
    buttongroup.getElements.asScala.foreach(buttongroup.remove)
    no_hosts_available.setVisible(true)
    btn_add.getActionListeners.foreach(btn_add.removeActionListener)
    tabbed.getComponents.foreach {
      case t if t == tab_main =>
      case t => tabbed.remove(t)
    }

    // fill
    if(!socket.killed) clientBox.setSelected(true)
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
        _frame.pack()
      }
    })
  }

  def getBack(self:JPanel) = {
    mainPanel.remove(self)
    tabbed.setVisible(true)
    init
    _frame.pack()
  }

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
    }
  }
  ExceptionHandler.registerExceptionHandler
}
