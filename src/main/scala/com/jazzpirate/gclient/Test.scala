package com.jazzpirate.gclient

import java.nio.file.Paths

import com.jazzpirate.gclient.fuse.CloudFuse
import com.jazzpirate.gclient.hosts.CloudDirectory
import com.jazzpirate.gclient.hosts.googledrive.TestDrive

object Test {
  def main(args: Array[String]) = {
    //ui.Main
    mountTest
  }

  def mountTest = try {
    FuseTest.mountCloud
    FuseTest.umount()
  } finally {
    FuseTest.umount()
  }
}

/* TODO
  -something something folders something
  - Photos
  - writing
  - sync
  - UI
 */

object FuseTest extends CloudFuse(TestDrive,Nil,"test",Settings.settingsFolder / "testmount")