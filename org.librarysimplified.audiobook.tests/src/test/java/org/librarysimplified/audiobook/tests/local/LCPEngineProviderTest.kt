package org.librarysimplified.audiobook.tests.local

import android.content.Context
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import org.librarysimplified.audiobook.tests.R
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

    Mockito.`when`(context.getString(eq(org.librarysimplified.audiobook.manifest.api.R.string.player_manifest_audiobook_default_track_n), any()))
      .thenReturn("Track #")

    return context
  }

  override fun onRealDevice(): Boolean {
    return false
  }
}
