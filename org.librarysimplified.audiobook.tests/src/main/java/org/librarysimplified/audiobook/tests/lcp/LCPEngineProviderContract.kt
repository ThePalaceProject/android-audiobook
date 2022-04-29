package org.librarysimplified.audiobook.tests.lcp

import android.content.Context
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.*
import org.librarysimplified.audiobook.lcp.LCPEngineProvider
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.open_access.ExoEngineProvider
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.audiobook.tests.DishonestDownloadProvider
import org.mockito.Mockito
import org.readium.r2.shared.publication.ContentProtection
import org.slf4j.Logger
import java.io.File
import java.net.URI

abstract class LCPEngineProviderContract {

  abstract fun log(): Logger

  abstract fun context(): Context

  /**
   * Check to see if the current test is executing as an instrumented test on a hardware or
   * emulated device, or if it's running as a local unit test with mocked Android components.
   *
   * @return `true` if the current test is running on a hardware or emulated device
   */

  abstract fun onRealDevice(): Boolean

  /**
   * Test that the engine accepts the Best New Horror book.
   */

  @Test
  fun lcpManifest_isAccepted() {
    val manifest = this.parseManifest("bestnewhorror.audiobook-manifest.json")
    val request = PlayerAudioEngineRequest(
      manifest = manifest,
      filter = { true },
      downloadProvider = DishonestDownloadProvider(),
      userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0"),
      file = Mockito.mock(File::class.java),
      contentProtections = listOf(Mockito.mock(ContentProtection::class.java))
    )
    val engine_provider = LCPEngineProvider()
    val book_provider = engine_provider.tryRequest(request)
    Assertions.assertNotNull(book_provider, "Engine must handle LCP manifest")
    val book_provider_nn = book_provider!!
    val result = book_provider_nn.create(this.context())
    this.log().debug("testAudioEnginesBestNewHorror: result: {}", result)
    Assertions.assertTrue(result is PlayerResult.Success, "Engine accepts LCP book")
  }

  /**
   * Test that the engine rejects an LCP book that has not been downloaded.
   */

  @Test
  fun notDownloadedBook_isRejected() {
    val manifest = this.parseManifest("bestnewhorror.audiobook-manifest.json")
    val request = PlayerAudioEngineRequest(
      manifest = manifest,
      filter = { true },
      downloadProvider = DishonestDownloadProvider(),
      userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0"),
      file = null,
      contentProtections = listOf(Mockito.mock(ContentProtection::class.java))
    )
    val engine_provider = LCPEngineProvider()
    val book_provider = engine_provider.tryRequest(request)
    Assertions.assertNull(book_provider, "Engine must reject not downloaded book")
  }

  /**
   * Test that the engine rejects the flatland (open access) book.
   */

  @Test
  fun openAccessManifest_isRejected() {
    val manifest = this.parseManifest("flatland.audiobook-manifest.json")
    val request = PlayerAudioEngineRequest(
      manifest = manifest,
      filter = { true },
      downloadProvider = DishonestDownloadProvider(),
      userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
    )
    val engine_provider = LCPEngineProvider()
    val book_provider = engine_provider.tryRequest(request)
    Assertions.assertNull(book_provider, "Engine must reject open access manifest")
  }

  /**
   * Test that the engine rejects the Summer Wives (Feedbooks) book.
   */

  @Test
  fun feedbooksManifest_isRejected() {
    val manifest = this.parseManifest("summerwives.audiobook-manifest.json")
    val request = PlayerAudioEngineRequest(
      manifest = manifest,
      filter = { true },
      downloadProvider = DishonestDownloadProvider(),
      userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
    )
    val engine_provider = LCPEngineProvider()
    val book_provider = engine_provider.tryRequest(request)
    Assertions.assertNull(book_provider, "Engine must reject Feedbooks manifest")
  }

  /**
   * Test that the player does not support streaming.
   */

  @Test
  fun supportsStreaming_isFalse() {
    val book = this.createBook("bestnewhorror.audiobook-manifest.json")
    Assertions.assertFalse(book.supportsStreaming, "Player does not support streaming")
  }

  private fun createBook(
    name: String,
    downloadProvider: PlayerDownloadProviderType = DishonestDownloadProvider()
  ): PlayerAudioBookType {

    val manifest = this.parseManifest(name)
    val request =
      PlayerAudioEngineRequest(
        manifest = manifest,
        filter = { true },
        downloadProvider = downloadProvider,
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0"),
        file = Mockito.mock(File::class.java),
        contentProtections = listOf(Mockito.mock(ContentProtection::class.java))
      )
    val engine_provider = LCPEngineProvider()
    val book_provider = engine_provider.tryRequest(request)
    Assertions.assertNotNull(book_provider, "Engine must handle manifest")
    val book_provider_nn = book_provider!!
    val result = book_provider_nn.create(this.context())
    this.log().debug("createBook: result: {}", result)

    val book = (result as PlayerResult.Success).result
    return book
  }

  private fun parseManifest(file: String): PlayerManifest {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:$file"),
        streams = this.resource(file),
        extensions = listOf()
      )
    this.log().debug("parseManifest: result: {}", result)
    Assertions.assertTrue(result is ParseResult.Success, "Result is success")
    val manifest = (result as ParseResult.Success).result
    return manifest
  }

  private fun resource(name: String): ByteArray {
    val path = "/org/librarysimplified/audiobook/tests/" + name
    return LCPEngineProviderContract::class.java.getResourceAsStream(path)?.readBytes()
      ?: throw AssertionError("Missing resource file: " + path)
  }
}
