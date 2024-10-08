package org.librarysimplified.audiobook.tests

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.license_check.api.LicenseCheckParameters
import org.librarysimplified.audiobook.license_check.api.LicenseCheckProviderType
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckParameters
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckProviderType
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckResult
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckType
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.manifest_parser.api.ManifestUnparsed
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.slf4j.Logger
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.ServiceLoader

abstract class LicenseCheckContract {

  private lateinit var eventLog: MutableList<SingleLicenseCheckStatus>

  abstract fun log(): Logger

  abstract fun licenseChecks(): LicenseCheckProviderType

  @TempDir
  @JvmField
  var tempFolder: File? = null

  @BeforeEach
  fun testSetup() {
    this.eventLog = mutableListOf()
  }

  /**
   * An empty list of license checks trivially succeeds.
   */

  @Test
  fun testEmptySucceeds() {
    val checks = this.licenseChecks()
    val manifest = this.manifest("ok_minimal_0.json")

    val parameters =
      LicenseCheckParameters(
        manifest = manifest,
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0"),
        checks = listOf(),
        cacheDirectory = File(tempFolder, "cache")
      )

    val result =
      checks.createLicenseCheck(parameters).use { check ->
        check.events.subscribe { event -> this.eventLog.add(event) }
        check.execute()
      }

    Assertions.assertEquals(0, result.checkStatuses.size)
    Assertions.assertTrue(result.checkSucceeded())
  }

  /**
   * A list of license checks with a single failing check fails.
   */

  @Test
  fun testOneFails() {
    val checks = this.licenseChecks()
    val manifest = this.manifest("ok_minimal_0.json")

    val parameters =
      LicenseCheckParameters(
        manifest = manifest,
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0"),
        checks = listOf(
          SucceedingTest(),
          SucceedingTest(),
          FailingTest(),
          SucceedingTest()
        ),
        cacheDirectory = File(tempFolder, "cache")
      )

    val result =
      checks.createLicenseCheck(parameters).use { check ->
        check.events.subscribe { event -> this.eventLog.add(event) }
        check.execute()
      }

    Assertions.assertEquals(4, result.checkStatuses.size)
    Assertions.assertFalse(result.checkSucceeded())
  }

  /**
   * A crashing check is treated as if the check failed.
   */

  @Test
  fun testOneCrashes() {
    val checks = this.licenseChecks()
    val manifest = this.manifest("ok_minimal_0.json")

    val parameters =
      LicenseCheckParameters(
        manifest = manifest,
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0"),
        checks = listOf(
          SucceedingTest(),
          SucceedingTest(),
          CrashingTest(),
          SucceedingTest()
        ),
        cacheDirectory = File(tempFolder, "cache")
      )

    val result =
      checks.createLicenseCheck(parameters).use { check ->
        check.events.subscribe { event -> this.eventLog.add(event) }
        check.execute()
      }

    Assertions.assertEquals(4, result.checkStatuses.size)
    Assertions.assertFalse(result.checkSucceeded())
  }

  /**
   * Non applicable tests succeed.
   */

  @Test
  fun testNonApplicable() {
    val checks = this.licenseChecks()
    val manifest = this.manifest("ok_minimal_0.json")

    val parameters =
      LicenseCheckParameters(
        manifest = manifest,
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0"),
        checks = listOf(
          NonApplicableTest(),
          NonApplicableTest(),
          NonApplicableTest(),
          NonApplicableTest()
        ),
        cacheDirectory = File(tempFolder, "cache")
      )

    val result =
      checks.createLicenseCheck(parameters).use { check ->
        check.events.subscribe { event -> this.eventLog.add(event) }
        check.execute()
      }

    Assertions.assertEquals(4, result.checkStatuses.size)
    Assertions.assertTrue(result.checkSucceeded())
  }

  private class NonApplicableTest : SingleLicenseCheckType, SingleLicenseCheckProviderType {
    override fun execute(): SingleLicenseCheckResult {
      return SingleLicenseCheckResult.NotApplicable("NotApplicable!")
    }

    override val name: String
      get() = "NonApplicable"

    override fun createLicenseCheck(
      parameters: SingleLicenseCheckParameters
    ): SingleLicenseCheckType {
      return this
    }
  }

  private class SucceedingTest : SingleLicenseCheckType, SingleLicenseCheckProviderType {
    override fun execute(): SingleLicenseCheckResult {
      return SingleLicenseCheckResult.Succeeded("Succeeded!")
    }

    override val name: String
      get() = "Succeeding"

    override fun createLicenseCheck(
      parameters: SingleLicenseCheckParameters
    ): SingleLicenseCheckType {
      return this
    }
  }

  private class FailingTest : SingleLicenseCheckType, SingleLicenseCheckProviderType {
    override fun execute(): SingleLicenseCheckResult {
      return SingleLicenseCheckResult.Failed("Failed!", null)
    }

    override val name: String
      get() = "Failing"

    override fun createLicenseCheck(
      parameters: SingleLicenseCheckParameters
    ): SingleLicenseCheckType {
      return this
    }
  }

  private class CrashingTest : SingleLicenseCheckType, SingleLicenseCheckProviderType {
    override fun execute(): SingleLicenseCheckResult {
      throw IOException("Crashing!")
    }

    override val name: String
      get() = "Crashing"

    override fun createLicenseCheck(
      parameters: SingleLicenseCheckParameters
    ): SingleLicenseCheckType {
      return this
    }
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
