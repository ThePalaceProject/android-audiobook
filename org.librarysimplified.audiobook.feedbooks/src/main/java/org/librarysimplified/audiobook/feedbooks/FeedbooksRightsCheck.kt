package org.librarysimplified.audiobook.feedbooks

import org.joda.time.LocalDateTime
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckParameters
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckResult
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckType

class FeedbooksRightsCheck(
  private val parameters: SingleLicenseCheckParameters,
  private val timeNow: LocalDateTime
) : SingleLicenseCheckType {

  override fun execute(): SingleLicenseCheckResult {
    this.event("Started rights check…")

    val rights =
      this.parameters.manifest.extensions.find { extension ->
        extension is FeedbooksRights
      } as FeedbooksRights?

    if (rights == null) {
      this.event("Check is not applicable: No rights information was provided.")
      return SingleLicenseCheckResult.NotApplicable("No rights information was provided.")
    }

    if (rights.validStart != null) {
      if (this.timeNow.isBefore(rights.validStart)) {
        this.event("Check failed: The current time precedes the start of the rights date range.")
        return SingleLicenseCheckResult.Failed(
          "The current time precedes the start of the rights date range."
        )
      }
    }

    if (rights.validEnd != null) {
      if (this.timeNow.isAfter(rights.validEnd)) {
        this.event("Check failed: The current time exceeds the end of the rights date range.")
        return SingleLicenseCheckResult.Failed(
          "The current time exceeds the end of the rights date range."
        )
      }
    }

    this.event("Check succeeded: The current time is within the specified date range.")
    return SingleLicenseCheckResult.Succeeded(
      "The current time is within the specified date range."
    )
  }

  private fun event(message: String) {
    this.parameters.onStatusChanged.invoke(
      SingleLicenseCheckStatus(
        source = "FeedbooksRightsCheck",
        message = message
      )
    )
  }
}
