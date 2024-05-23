package org.librarysimplified.audiobook.tests

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerAudioEngines
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.slf4j.Logger
import java.net.URI

/**
 * Tests for the {@link org.librarysimplified.audiobook.api.PlayerAudioEngines} type.
 */

abstract class PlayerAudioEnginesContract {

  abstract fun log(): Logger

  @Test
  fun testAudioEnginesTrivial() {
    val manifest = parseManifest("ok_minimal_0.json")
    val request = PlayerAudioEngineRequest(
      manifest = manifest,
      filter = { true },
      downloadProvider = DishonestDownloadProvider(),
      bookFile = null,
      userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
    )
    val providers = PlayerAudioEngines.findAllFor(request)
    Assertions.assertEquals(1, providers.size, "Exactly one open access provider should be present")
  }

  @Test
  fun testAudioEnginesAllFiltered() {
    val manifest = parseManifest("ok_minimal_0.json")
    val request = PlayerAudioEngineRequest(
      manifest = manifest,
      filter = { false },
      downloadProvider = DishonestDownloadProvider(),
      bookFile = null,
      userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
    )
    val providers = PlayerAudioEngines.findAllFor(request)
    Assertions.assertEquals(0, providers.size, "No providers should be present")
  }

  private fun parseManifest(file: String): PlayerManifest {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:$file"),
        streams = resource(file),
        extensions = listOf()
      )

    this.log().debug("result: {}", result)
    Assertions.assertTrue(result is ParseResult.Success, "Result is success")
    val manifest = (result as ParseResult.Success).result
    return manifest
  }

  private fun resource(name: String): ByteArray {
    val path = "/org/librarysimplified/audiobook/tests/" + name
    return PlayerAudioEnginesContract::class.java.getResourceAsStream(path)?.readBytes()
      ?: throw AssertionError("Missing resource file: " + path)
  }
}
