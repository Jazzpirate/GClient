package com.jazzpirate.gclient.hosts.googledrive

import java.awt.event.{ActionEvent, ActionListener}

import com.jazzpirate.gclient.Settings
import com.jazzpirate.gclient.ui.{Main, NewAccount}
import javax.swing.JTextField

class AddAccountForm(parent:NewAccount) extends AddAcountFormJava {
  OKButton.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      import javax.swing.JOptionPane
      val name = parent.nameTextField.getText
      val optionPane = new JOptionPane(
        "Create new Account \"" + name + "\"?",
        JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION)
      val diag = optionPane.createDialog("New Account")
      diag.setVisible(true)
      while (diag.isVisible) {
        Thread.sleep(100)
      }
      optionPane.getValue match {
        case 0 =>
          Google.getCredentials(name)
          Settings.settings.addAccount(name,Google)
          Main.getBack(parent.top_panel)
          // do something
        case _ =>
      }
    }
  })
}
