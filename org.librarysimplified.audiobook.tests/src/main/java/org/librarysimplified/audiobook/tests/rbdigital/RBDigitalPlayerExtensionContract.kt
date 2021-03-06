package org.librarysimplified.audiobook.tests.rbdigital

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.rbdigital.RBDigitialPlayerExtension

import java.util.ServiceLoader

abstract class RBDigitalPlayerExtensionContract {

  @Test
  fun hasCorrectServiceType() {
    val extensions = ServiceLoader.load(PlayerExtensionType::class.java).toList()
    Assertions.assertTrue(extensions.any({ extension -> extension is RBDigitialPlayerExtension }))
  }
}
