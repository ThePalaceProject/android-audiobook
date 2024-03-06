package org.librarysimplified.audiobook.tests

import android.app.Application
import org.librarysimplified.audiobook.tests.open_access.ExoEngineProviderContract
import org.mockito.Mockito
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ExoEngineProviderTest : ExoEngineProviderContract() {

  override fun log(): Logger {
    return LoggerFactory.getLogger(ExoEngineProviderTest::class.java)
  }

  override fun context(): Application {
    return Mockito.mock(Application::class.java)
  }

  override fun onRealDevice(): Boolean {
    return false
  }
}
