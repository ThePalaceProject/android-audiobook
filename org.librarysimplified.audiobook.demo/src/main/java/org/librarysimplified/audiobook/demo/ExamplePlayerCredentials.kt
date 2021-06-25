package org.librarysimplified.audiobook.demo

import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAPassword
import java.io.Serializable

sealed class ExamplePlayerCredentials : Serializable {

  object None : ExamplePlayerCredentials()

  data class Basic(
    val userName: String,
    val password: String
  ) : ExamplePlayerCredentials()

  data class Overdrive(
    val userName: String,
    val password: OPAPassword,
    val clientKey: String,
    val clientPass: String
  ) : ExamplePlayerCredentials()

  data class Feedbooks(
    val userName: String,
    val password: String,
    val bearerTokenSecret: ByteArray,
    val issuerURL: String
  ) : ExamplePlayerCredentials()
}
