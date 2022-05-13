package org.librarysimplified.audiobook.tests.open_access

import android.content.Context
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerAudioEngines
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.audiobook.tests.ExoUriDownloadProvider
import org.slf4j.Logger
import java.net.URI

abstract class ExoDownloadContract {

  abstract fun log(): Logger

  abstract fun context(): Context

  @Test
  fun testDownloadFlatlandURIsJustOnce() {

    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:flatland"),
        streams = resource("flatland_toc.audiobook-manifest.json"),
        extensions = listOf()
      )

    this.log().debug("result: {}", result)
    Assertions.assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result

    // create a hashmap with the list of URIs to download and the number of times they were downloaded
    val urisDownloadMap = hashMapOf<URI, Int>()
    manifest.readingOrder.forEach {
      if (it.hrefURI != null) {
        urisDownloadMap[it.hrefURI!!] = 0
      }
    }

    val engine =
      PlayerAudioEngines.findBestFor(
        PlayerAudioEngineRequest(
          manifest = manifest,
          filter = { true },
          downloadProvider = ExoUriDownloadProvider(urisDownloadMap),
          userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
        )
      )

    Assertions.assertNotNull(engine)

    val bookResult =
      engine?.bookProvider?.create(
        context = context(),
        extensions = listOf()
      )

    Assertions.assertNotNull(engine)

    val audiobook = (bookResult as PlayerResult.Success).result

    // start downloading the book
    audiobook.wholeBookDownloadTask.fetch()

    // confirm that there are no new URIs added to the hashMap
    Assertions.assertTrue(urisDownloadMap.size == manifest.readingOrder.size)

    // confirm that all the URIs were downloaded just once
    urisDownloadMap.keys.forEach {
      Assertions.assertTrue(urisDownloadMap[it] == 1)
    }
  }

  private fun resource(name: String): ByteArray {
    val path = "/org/librarysimplified/audiobook/tests/" + name
    return ExoManifestContract::class.java.getResourceAsStream(path)?.readBytes()
      ?: throw AssertionError("Missing resource file: " + path)
  }
}
