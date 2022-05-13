package org.librarysimplified.audiobook.tests.local

import android.content.Context
import org.librarysimplified.audiobook.tests.open_access.ExoDownloadContract
import org.mockito.Mockito
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ExoDownloadTest : ExoDownloadContract() {
  override fun log(): Logger {
    return LoggerFactory.getLogger(ExoDownloadTest::class.java)
  }

  override fun context(): Context {
    return Mockito.mock(Context::class.java)
  }
}
