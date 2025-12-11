package org.librarysimplified.audiobook.manifest_fulfill.basic

/**
 * Parameters for fetching manifest from a URI using basic authentication.
 */

data class ManifestFulfillmentCredentialsBasic(
  val userName: String,
  val password: String
) : ManifestFulfillmentCredentialsType
