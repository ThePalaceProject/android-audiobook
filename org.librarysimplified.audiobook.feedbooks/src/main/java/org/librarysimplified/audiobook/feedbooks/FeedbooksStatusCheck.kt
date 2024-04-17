package org.librarysimplified.audiobook.feedbooks

import okhttp3.OkHttpClient
import okhttp3.Request
import org.librarysimplified.audiobook.lcp.license_status.LicenseStatusDocument
import org.librarysimplified.audiobook.lcp.license_status.LicenseStatusDocument.Status.ACTIVE
import org.librarysimplified.audiobook.lcp.license_status.LicenseStatusDocument.Status.CANCELLED
import org.librarysimplified.audiobook.lcp.license_status.LicenseStatusDocument.Status.EXPIRED
import org.librarysimplified.audiobook.lcp.license_status.LicenseStatusDocument.Status.READY
import org.librarysimplified.audiobook.lcp.license_status.LicenseStatusDocument.Status.RETURNED
import org.librarysimplified.audiobook.lcp.license_status.LicenseStatusDocument.Status.REVOKED
import org.librarysimplified.audiobook.lcp.license_status.LicenseStatusParserProviderType
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckParameters
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckResult
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.audiobook.parser.api.ParserType
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * An LCP license status check.
 */

class FeedbooksStatusCheck(
  private val httpClient: OkHttpClient,
  private val parsers: LicenseStatusParserProviderType,
  private val parameters: SingleLicenseCheckParameters
) : SingleLicenseCheckType {

  private val logger =
    LoggerFactory.getLogger(FeedbooksStatusCheck::class.java)

  private fun event(message: String) {
    this.parameters.onStatusChanged.invoke(
      SingleLicenseCheckStatus(
        source = "FeedbooksStatusCheck",
        message = message
      )
    )
  }

  override fun execute(): SingleLicenseCheckResult {
    this.event("Started status check…")

    return when (val link = this.parameters.manifest.links.find(this::linkIsLicenseLink)) {
      null -> {
        this.event("Check is not applicable: No license link.")
        SingleLicenseCheckResult.NotApplicable("No license link.")
      }
      is PlayerManifestLink.LinkBasic -> {
        val href = link.href
        if (href == null) {
          SingleLicenseCheckResult.NotApplicable("No license link.")
        } else {
          this.checkLink(href)
        }
      }
      is PlayerManifestLink.LinkTemplated -> {
        this.event("Check is not applicable: Templated links are not supported.")
        SingleLicenseCheckResult.NotApplicable("Templated links are not supported.")
      }
    }
  }

  private fun linkIsLicenseLink(link: PlayerManifestLink) =
    (link.relation.contains("license") && "application/vnd.readium.license.status.v1.0+json" == link.type?.fullType)

  private fun checkLink(target: URI): SingleLicenseCheckResult {
    this.logger.debug("fetching license document")
    this.event("Fetching license document $target…")

    val request =
      Request.Builder()
        .url(target.toURL())
        .header("User-Agent", this.parameters.userAgent.userAgent)
        .build()

    return this.httpClient.newCall(request).execute().use { response ->
      this.logger.debug("response: {} {}", response.code, response.message)

      if (!response.isSuccessful) {
        this.event("Check is not applicable: The server failed to produce a license status document.")
        return SingleLicenseCheckResult.NotApplicable(
          "The server failed to produce a license status document."
        )
      }

      val body = response.body?.bytes() ?: ByteArray(0)
      this.logger.debug("received {} bytes", body.size)
      this.parsers.createParser(target, body).use(this::parseLicenseStatusDocument)
    }
  }

  private fun parseLicenseStatusDocument(
    parser: ParserType<LicenseStatusDocument>
  ): SingleLicenseCheckResult {
    return when (val parseResult = parser.parse()) {
      is ParseResult.Success -> {
        val document = parseResult.result
        this.logger.debug("license status: {}", document.status)
        when (document.status) {
          READY,
          ACTIVE -> {
            this.event("Check succeeded: License status is ${document.status}")
            SingleLicenseCheckResult.Succeeded("License status is ${document.status}")
          }
          REVOKED,
          RETURNED,
          CANCELLED,
          EXPIRED -> {
            this.event("Check failed: License status is ${document.status}")
            SingleLicenseCheckResult.Failed("License status is ${document.status}")
          }
        }
      }
      is ParseResult.Failure -> {
        for (error in parseResult.errors) {
          this.logger.error(
            "parse error: {}:{}:{}: {}",
            error.source,
            error.line,
            error.column,
            error.message
          )
          this.event("License parse error: ${error.source}: ${error.line}:${error.column}: ${error.message}")
        }

        this.event("Check is not applicable: The server produced an unparseable license status document.")
        return SingleLicenseCheckResult.NotApplicable(
          "The server produced an unparseable license status document."
        )
      }
    }
  }
}
