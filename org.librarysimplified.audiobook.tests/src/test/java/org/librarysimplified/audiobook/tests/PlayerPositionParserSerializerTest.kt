package org.librarysimplified.audiobook.tests

import org.librarysimplified.audiobook.api.PlayerPositionParserType
import org.librarysimplified.audiobook.api.PlayerPositionSerializerType
import org.librarysimplified.audiobook.api.PlayerPositions
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PlayerPositionParserSerializerTest : PlayerPositionParserSerializerContract() {

  override fun logger(): Logger {
    return LoggerFactory.getLogger(PlayerPositionParserSerializerTest::class.java)
  }

  override fun createParser(): PlayerPositionParserType {
    return PlayerPositions
  }

  override fun createSerializer(): PlayerPositionSerializerType {
    return PlayerPositions
  }
}
