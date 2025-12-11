package org.librarysimplified.audiobook.manifest_fulfill.basic

/**
 * Credentials using an access token (such as SAML).
 */

data class ManifestFulfillmentCredentialsToken(
  val token: String
) : ManifestFulfillmentCredentialsType
