package org.librarysimplified.audiobook.tests

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Tests for the {@link org.librarysimplified.audiobook.api.PlayerAudioEngines} type.
 */

class PlayerAudioEnginesTest : PlayerAudioEnginesContract() {

  override fun log(): Logger {
    return LoggerFactory.getLogger(PlayerAudioEnginesTest::class.java)
  }
}
