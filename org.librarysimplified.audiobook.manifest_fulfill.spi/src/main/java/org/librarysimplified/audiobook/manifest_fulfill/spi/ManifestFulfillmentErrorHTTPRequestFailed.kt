package org.librarysimplified.audiobook.manifest_fulfill.spi

/**
 * A generic error that indicates that an HTTP server request failed.
 */

data class ManifestFulfillmentErrorHTTPRequestFailed(
  override val message: String,
  override val serverData: ManifestFulfillmentErrorType.ServerData
) : ManifestFulfillmentErrorType
