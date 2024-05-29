package org.librarysimplified.audiobook.tests.open_access

import android.app.Application
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerAudioEngines
import org.librarysimplified.audiobook.api.PlayerBookCredentialsNone
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.media3.ExoReadingOrderItemHandle
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.slf4j.Logger
import java.net.URI

abstract class ExoDownloadContract {

  abstract fun log(): Logger

  abstract fun context(): Application

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
    manifest.readingOrder.forEach { item ->
      urisDownloadMap[item.link.hrefURI!!] = 0
    }

    var audiobook: PlayerAudioBookType? = null

    val engine =
      PlayerAudioEngines.findBestFor(
        PlayerAudioEngineRequest(
          manifest = manifest,
          filter = { true },
          downloadProvider = org.librarysimplified.audiobook.tests.ExoUriDownloadProvider(
            onRequestSuccessfullyCompleted = { uri ->
              updateAudiobookSpineItemsWithUri(audiobook, uri)
            },
            uriDownloadTimes = urisDownloadMap
          ),
          userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0"),
          bookFile = null,
          bookCredentials = PlayerBookCredentialsNone
        )
      )

    Assertions.assertNotNull(engine)

    val bookResult =
      engine?.bookProvider?.create(
        context = context(),
        extensions = listOf()
      )

    Assertions.assertNotNull(engine)

    audiobook = (bookResult as PlayerResult.Success).result

    // start downloading the book
    audiobook.wholeBookDownloadTask.fetch()

    // confirm that there are no new URIs added to the hashMap
    Assertions.assertTrue(urisDownloadMap.size == manifest.readingOrder.size)

    // confirm that all the URIs were downloaded just once
    urisDownloadMap.keys.forEach {
      Assertions.assertTrue(urisDownloadMap[it] == 1)
    }
  }

  private fun updateAudiobookSpineItemsWithUri(
    audioBook: PlayerAudioBookType?,
    uri: URI
  ) {
    audioBook?.downloadTasks?.find { task ->
      task.readingOrderItems.filterIsInstance<ExoReadingOrderItemHandle>().any { item ->
        item.itemManifest.item.link.hrefURI == uri
      }
    }?.readingOrderItems?.filterIsInstance<ExoReadingOrderItemHandle>()?.forEach { spineItem ->
      spineItem.setDownloadStatus(PlayerReadingOrderItemDownloaded(spineItem))
    }
  }

  private fun resource(name: String): ByteArray {
    val path = "/org/librarysimplified/audiobook/tests/" + name
    return ExoManifestTest::class.java.getResourceAsStream(path)?.readBytes()
      ?: throw AssertionError("Missing resource file: " + path)
  }
}
