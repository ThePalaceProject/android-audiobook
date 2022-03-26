package org.librarysimplified.audiobook.tests.local

import android.content.Context
import org.librarysimplified.audiobook.tests.lcp.LCPTitlesContract
import org.mockito.Mockito
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LCPTitlesTest : LCPTitlesContract() {
  override fun log(): Logger {
    return LoggerFactory.getLogger(LCPTitlesTest::class.java)
  }

  override fun context(): Context {
    return Mockito.mock(Context::class.java)
  }
}
