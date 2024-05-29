package org.librarysimplified.audiobook.lcp.downloads

import org.readium.r2.lcp.license.model.LicenseDocument

/**
 * A parsed LCP license, and the serialized bytes of the license.
 */

data class LCPLicenseAndBytes(
  val license: LicenseDocument,
  val licenseBytes: ByteArray
)
