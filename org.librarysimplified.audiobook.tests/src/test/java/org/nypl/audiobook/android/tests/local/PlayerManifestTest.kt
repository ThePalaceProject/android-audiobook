package org.nypl.audiobook.android.tests.local

import org.nypl.audiobook.android.tests.PlayerManifestContract
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Tests for the {@link org.librarysimplified.audiobook.api.PlayerManifest} type.
 */

class PlayerManifestTest : PlayerManifestContract() {

  override fun log(): Logger {
    return LoggerFactory.getLogger(PlayerManifestTest::class.java)
  }

}
