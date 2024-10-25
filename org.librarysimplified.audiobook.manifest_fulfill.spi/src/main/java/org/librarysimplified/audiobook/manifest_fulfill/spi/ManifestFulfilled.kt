package org.librarysimplified.audiobook.manifest_fulfill.spi

import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import java.net.URI

/**
 * A downloaded manifest.
 */

data class ManifestFulfilled(
  val source: URI?,
  val contentType: MIMEType,
  val authorization: LSHTTPAuthorizationType? = null,
  val data: ByteArray
)
