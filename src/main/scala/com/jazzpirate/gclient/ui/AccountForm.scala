package com.jazzpirate.gclient.ui

import com.jazzpirate.gclient.hosts.Account

class AccountForm(acc:Account) extends AccountJava {
  account_name.setText(acc.account_name)

}
