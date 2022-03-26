package org.librarysimplified.audiobook.tests.lcp

import android.content.Context
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.*
import org.librarysimplified.audiobook.lcp.LCPEngineProvider
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.audiobook.tests.DishonestDownloadProvider
import org.mockito.Mockito
import org.readium.r2.shared.publication.ContentProtection
import org.slf4j.Logger
import java.io.File
import java.net.URI

abstract class LCPTitlesContract {

  abstract fun log(): Logger

  abstract fun context(): Context

  /**
   * Test that the book chapter title is coming from the 'toc' object of the manifest.
   */

  @Test
  fun testTitlesBeingDisplayedFromToc() {
    val manifest = this.createManifest("bestnewhorror.audiobook-manifest.json")
    val firstTocElement = manifest.toc?.firstOrNull()
    val lastTocElement = manifest.toc?.lastOrNull()
    Assertions.assertEquals("9780061552137_001_MP3.mp3", manifest.readingOrder.first().title)
    Assertions.assertEquals("9780061552137_007_MP3.mp3", manifest.readingOrder.last().title)
    Assertions.assertNotNull(firstTocElement)
    Assertions.assertNotNull(lastTocElement)

    val book = this.createBook(manifest)
    Assertions.assertEquals(firstTocElement?.title, book.spine.first().title)
    Assertions.assertEquals(lastTocElement?.title, book.spine.last().title)
  }

  private fun createManifest(name: String): PlayerManifest {
    return this.parseManifest(name)
  }

  private fun createBook(
    manifest: PlayerManifest,
    downloadProvider: PlayerDownloadProviderType = DishonestDownloadProvider()
  ): PlayerAudioBookType {

    val request =
      PlayerAudioEngineRequest(
        manifest = manifest,
        filter = { true },
        downloadProvider = downloadProvider,
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0"),
        file = Mockito.mock(File::class.java),
        contentProtections = listOf(Mockito.mock(ContentProtection::class.java))
      )
    val engineProvider = LCPEngineProvider()
    val bookProvider = engineProvider.tryRequest(request)
    Assertions.assertNotNull(bookProvider, "Engine must handle manifest")
    val result = bookProvider?.create(this.context())
    this.log().debug("createBook: result: {}", result)

    return (result as PlayerResult.Success).result
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
    return (result as ParseResult.Success).result
  }

  private fun resource(name: String): ByteArray {
    val path = "/org/librarysimplified/audiobook/tests/$name"
    return LCPEngineProviderContract::class.java.getResourceAsStream(path)?.readBytes()
      ?: throw AssertionError("Missing resource file: $path")
  }
}
