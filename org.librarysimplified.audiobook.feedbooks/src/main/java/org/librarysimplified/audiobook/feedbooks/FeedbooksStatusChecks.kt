package org.librarysimplified.audiobook.feedbooks

import org.librarysimplified.audiobook.lcp.license_status.LicenseStatusParsers
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckParameters
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckProviderType
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckType

class FeedbooksStatusChecks : SingleLicenseCheckProviderType {

  override val name: String =
    "FeedbooksRightsCheck"

  override fun createLicenseCheck(
    parameters: SingleLicenseCheckParameters
  ): SingleLicenseCheckType {
    return FeedbooksStatusCheck(
      parsers = LicenseStatusParsers,
      parameters = parameters
    )
  }
}
