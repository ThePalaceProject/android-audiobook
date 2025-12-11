package org.librarysimplified.audiobook.manifest_fulfill.basic

import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyParametersType
import org.librarysimplified.http.api.LSHTTPClientType
import java.net.URI

/**
 * Parameters for fetching manifest from a URI using (optional) basic authentication.
 */

data class ManifestFulfillmentBasicParameters(
  val uri: URI,
  val credentials: ManifestFulfillmentCredentialsType?,
  val httpClient: LSHTTPClientType,
  override val userAgent: PlayerUserAgent
) : ManifestFulfillmentStrategyParametersType
