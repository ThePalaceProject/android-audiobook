package org.librarysimplified.audiobook.api

/**
 * An LCP passphrase.
 */

data class PlayerBookCredentialsLCP(
  val passphrase: String
) : PlayerBookCredentialsType
