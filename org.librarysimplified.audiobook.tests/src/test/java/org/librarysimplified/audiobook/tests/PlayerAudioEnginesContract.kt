package org.librarysimplified.audiobook.tests

import android.app.Application
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerAudioEngines
import org.librarysimplified.audiobook.api.PlayerBookCredentialsNone
import org.librarysimplified.audiobook.api.PlayerBookSource
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.manifest_parser.api.ManifestUnparsed
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPNetworkAccess
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.slf4j.Logger
import java.net.URI

/**
 * Tests for the {@link org.librarysimplified.audiobook.api.PlayerAudioEngines} type.
 */

abstract class PlayerAudioEnginesContract {

  abstract fun log(): Logger

  private lateinit var httpClient: LSHTTPClientType

  @BeforeEach
  fun testSetup() {
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
  fun testAudioEnginesTrivial() {
    val manifest = parseManifest("ok_minimal_0.json")
    val request = PlayerAudioEngineRequest(
      authorizationHandler = NullAuthorizationHandler(),
      manifest = manifest,
      filter = { true },
      downloadProvider = DishonestDownloadProvider(),
      httpClient = this.httpClient,
      bookCredentials = PlayerBookCredentialsNone,
      bookSource = PlayerBookSource.PlayerBookSourceManifestOnly
    )
    val providers = PlayerAudioEngines.findAllFor(request)
    Assertions.assertEquals(1, providers.size, "Exactly one open access provider should be present")
  }

  @Test
  fun testAudioEnginesAllFiltered() {
    val manifest = parseManifest("ok_minimal_0.json")
    val request = PlayerAudioEngineRequest(
      authorizationHandler = NullAuthorizationHandler(),
      manifest = manifest,
      filter = { false },
      downloadProvider = DishonestDownloadProvider(),
      httpClient = this.httpClient,
      bookCredentials = PlayerBookCredentialsNone,
      bookSource = PlayerBookSource.PlayerBookSourceManifestOnly
    )
    val providers = PlayerAudioEngines.findAllFor(request)
    Assertions.assertEquals(0, providers.size, "No providers should be present")
  }

  private fun parseManifest(file: String): PlayerManifest {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:$file"),
        input = resource(file),
        extensions = listOf()
      )

    this.log().debug("result: {}", result)
    Assertions.assertTrue(result is ParseResult.Success, "Result is success")
    val manifest = (result as ParseResult.Success).result
    return manifest
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
