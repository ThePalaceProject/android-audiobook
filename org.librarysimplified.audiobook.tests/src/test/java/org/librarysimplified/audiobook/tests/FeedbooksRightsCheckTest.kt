package org.librarysimplified.audiobook.tests

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FeedbooksRightsCheckTest : FeedbooksRightsCheckContract() {

  override fun log(): Logger {
    return LoggerFactory.getLogger(FeedbooksRightsCheckTest::class.java)
  }
}
