package org.librarysimplified.audiobook.lcp.downloads

import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType

data class LCPLicenseFailure(
  override val message: String,
) : ManifestFulfillmentErrorType {
  override val serverData: ManifestFulfillmentErrorType.ServerData? =
    null
}
