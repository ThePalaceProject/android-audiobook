package org.librarysimplified.audiobook.tests

import android.app.Application
import android.content.Context
import org.librarysimplified.audiobook.tests.open_access.ExoDownloadContract
import org.mockito.Mockito
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ExoDownloadTest : ExoDownloadContract() {
  override fun log(): Logger {
    return LoggerFactory.getLogger(ExoDownloadTest::class.java)
  }

  override fun context(): Application {
    return Mockito.mock(Application::class.java)
  }
}
