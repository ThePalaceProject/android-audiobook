package org.librarysimplified.audiobook.manifest_fulfill.spi

import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPAuthorizationType

/**
 * A downloaded manifest.
 */

data class ManifestFulfilled(
  val contentType: MIMEType,
  val authorization: LSHTTPAuthorizationType? = null,
  val data: ByteArray
)
