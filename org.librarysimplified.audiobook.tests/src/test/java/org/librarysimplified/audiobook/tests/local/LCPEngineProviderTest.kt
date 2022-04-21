package org.librarysimplified.audiobook.tests.local

import android.content.Context
import org.librarysimplified.audiobook.tests.lcp.LCPEngineProviderContract
import org.mockito.Mockito
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LCPEngineProviderTest : LCPEngineProviderContract() {
  override fun log(): Logger {
    return LoggerFactory.getLogger(LCPEngineProviderTest::class.java)
  }

  override fun context(): Context {
    val context = Mockito.mock(Context::class.java)
    return context
  }

  override fun onRealDevice(): Boolean {
    return false
  }
}
