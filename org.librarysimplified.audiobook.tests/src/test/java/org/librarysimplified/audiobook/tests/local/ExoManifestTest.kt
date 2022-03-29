package org.librarysimplified.audiobook.tests.local

import android.content.Context
import org.librarysimplified.audiobook.tests.open_access.ExoManifestContract
import org.mockito.Mockito
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ExoManifestTest : ExoManifestContract() {
  override fun log(): Logger {
    return LoggerFactory.getLogger(ExoManifestTest::class.java)
  }

  override fun context(): Context {
    return Mockito.mock(Context::class.java)
  }
}
