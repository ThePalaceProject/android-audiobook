package org.librarysimplified.audiobook.demo

import java.io.Serializable

/**
 * The parameters used to fetch a remote manifest.
 */

data class ExamplePlayerParameters(
  val credentials: ExamplePlayerCredentials,
  val fetchURI: String
) : Serializable
