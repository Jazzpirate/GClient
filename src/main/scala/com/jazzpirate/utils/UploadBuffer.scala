package com.jazzpirate.utils

import com.jazzpirate.gclient.{AbstractSettings, Settings}
import com.jazzpirate.gclient.hosts.Account
import info.kwarc.mmt.api.utils.File

class UploadBuffer(acc:Account) {
  val folder = Settings.settingsFolder / acc.account_name
  if (!folder.exists()) folder.mkdirs()
  val file = folder / "upload.json"
  if (!file.exists()) File.write(file,"[]")
  private object MySettings extends AbstractSettings(file) {

  }
}
