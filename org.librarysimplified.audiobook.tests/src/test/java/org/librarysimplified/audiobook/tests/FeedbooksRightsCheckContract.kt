package org.librarysimplified.audiobook.tests

import android.app.Application
import org.joda.time.LocalDateTime
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.feedbooks.FeedbooksRightsCheck
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckParameters
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckResult
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.manifest_parser.api.ManifestUnparsed
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPNetworkAccess
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.librarysimplified.http.vanilla.internal.LSHTTPClient
import org.mockito.Mockito
import org.slf4j.Logger
import java.io.File
import java.net.URI
import java.util.ServiceLoader

abstract class FeedbooksRightsCheckContract {

  private lateinit var eventLog: MutableList<SingleLicenseCheckStatus>

  private lateinit var httpClient: LSHTTPClientType


  abstract fun log(): Logger

  @TempDir
  @JvmField
  var tempFolder: File? = null

  @BeforeEach
  fun testSetup() {
    this.eventLog = mutableListOf()
    this.httpClient =
      LSHTTPClients()
        .create(
          Mockito.mock(Application::class.java),
          LSHTTPClientConfiguration(
            applicationName = "org.thepalaceproject.audiobook.tests",
            applicationVersion = "1.0.0",
            networkAccess = LSHTTPNetworkAccess
          )
        )
  }

  @Test
  fun testNotApplicable() {
    val manifest = this.manifest("ok_minimal_0.json")

    val result =
      FeedbooksRightsCheck(
        parameters = SingleLicenseCheckParameters(
          manifest = manifest,
          httpClient = this.httpClient,
          onStatusChanged = { },
          cacheDirectory = File(tempFolder, "cache")
        ),
        timeNow = LocalDateTime.now()
      ).execute()

    Assertions.assertTrue(result is SingleLicenseCheckResult.NotApplicable)
  }

  @Test
  fun testOK() {
    val manifest = this.manifest("feedbooks_rights_ok.json")

    val result =
      FeedbooksRightsCheck(
        parameters = SingleLicenseCheckParameters(
          manifest = manifest,
          httpClient = this.httpClient,
          onStatusChanged = { },
          cacheDirectory = File(tempFolder, "cache")
        ),
        timeNow = LocalDateTime.parse("2000-01-01T00:10:00.000")
      ).execute()

    Assertions.assertTrue(result is SingleLicenseCheckResult.Succeeded)
  }

  @Test
  fun testInvalidBefore() {
    val manifest = this.manifest("feedbooks_rights_ok.json")

    val result =
      FeedbooksRightsCheck(
        parameters = SingleLicenseCheckParameters(
          manifest = manifest,
          httpClient = this.httpClient,
          onStatusChanged = { },
          cacheDirectory = File(tempFolder, "cache")
        ),
        timeNow = LocalDateTime.parse("1999-01-01T00:10:00.000")
      ).execute()

    val failed = result as SingleLicenseCheckResult.Failed
    Assertions.assertTrue(failed.message.contains("precedes"))
  }

  @Test
  fun testInvalidAfter() {
    val manifest = this.manifest("feedbooks_rights_ok.json")

    val result =
      FeedbooksRightsCheck(
        parameters = SingleLicenseCheckParameters(
          manifest = manifest,
          httpClient = this.httpClient,
          onStatusChanged = { },
          cacheDirectory = File(tempFolder, "cache")
        ),
        timeNow = LocalDateTime.parse("2002-01-01T00:10:00.000")
      ).execute()

    val failed = result as SingleLicenseCheckResult.Failed
    Assertions.assertTrue(failed.message.contains("exceeds"))
  }

  @Test
  fun testOKBeforeUnspecified() {
    val manifest = this.manifest("feedbooks_rights_no_start.json")

    val result =
      FeedbooksRightsCheck(
        parameters = SingleLicenseCheckParameters(
          manifest = manifest,
          httpClient = this.httpClient,
          onStatusChanged = { },
          cacheDirectory = File(tempFolder, "cache")
        ),
        timeNow = LocalDateTime.parse("2000-01-01T00:10:00.000")
      ).execute()

    Assertions.assertTrue(result is SingleLicenseCheckResult.Succeeded)
  }

  @Test
  fun testOKAfterUnspecified() {
    val manifest = this.manifest("feedbooks_rights_no_end.json")

    val result =
      FeedbooksRightsCheck(
        parameters = SingleLicenseCheckParameters(
          manifest = manifest,
          httpClient = this.httpClient,
          onStatusChanged = { },
          cacheDirectory = File(tempFolder, "cache")
        ),
        timeNow = LocalDateTime.parse("2000-01-01T00:10:00.000")
      ).execute()

    Assertions.assertTrue(result is SingleLicenseCheckResult.Succeeded)
  }

  private fun manifest(
    name: String
  ): PlayerManifest {
    val result =
      ManifestParsers.parse(
        uri = URI.create(name),
        input = this.resource(name),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    Assertions.assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    return success.result
  }

  private fun resource(name: String): ManifestUnparsed {
    val path = "/org/librarysimplified/audiobook/tests/" + name
    return ManifestUnparsed(
      palaceId = PlayerPalaceID(path),
      data = ResourceMarker::class.java.getResourceAsStream(path)?.readBytes()
        ?: throw AssertionError("Missing resource file: " + path)
    )
  }
}
