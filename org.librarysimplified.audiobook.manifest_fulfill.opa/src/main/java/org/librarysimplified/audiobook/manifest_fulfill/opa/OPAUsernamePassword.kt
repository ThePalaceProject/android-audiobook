package org.librarysimplified.audiobook.manifest_fulfill.opa

/**
 * A convenient username/password pair.
 */

data class OPAUsernamePassword(
  val userName: String,
  val password: OPAPassword
)
