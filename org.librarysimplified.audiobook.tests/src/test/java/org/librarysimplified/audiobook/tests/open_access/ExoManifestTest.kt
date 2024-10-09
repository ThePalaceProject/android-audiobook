package org.librarysimplified.audiobook.tests.open_access

import android.app.Application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerMissingTrackNameGeneratorType
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.manifest_parser.api.ManifestUnparsed
import org.librarysimplified.audiobook.media3.ExoManifest
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.audiobook.tests.ResourceMarker
import org.mockito.Mockito
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * Tests for the {@link org.librarysimplified.audiobook.api.PlayerRawManifest} type.
 */

class ExoManifestTest {

  private val missingNames =
    object : PlayerMissingTrackNameGeneratorType {
      override fun generateName(trackIndex: Int): String {
        return "Track ${trackIndex + 1}"
      }
    }

  fun log(): Logger =
    LoggerFactory.getLogger(ExoManifestTest::class.java)

  fun context(): Application =
    Mockito.mock(Application::class.java)

  @Test
  fun testOkFlatlandGardeur() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:flatland"),
        input = resource("flatland.audiobook-manifest.json"),
        extensions = listOf()
      )

    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result

    val exoResult =
      ExoManifest.transform(
        manifest = manifest,
        bookID = PlayerBookID.transform("x"),
        missingTrackNames = this.missingNames
      )
    dumpResult(exoResult)
    assertTrue(exoResult is PlayerResult.Success, "Result is success")

    val exoSuccess: PlayerResult.Success<ExoManifest, Exception> =
      exoResult as PlayerResult.Success<ExoManifest, Exception>

    val exo = exoSuccess.result

    assertEquals(
      "Flatland: A Romance of Many Dimensions",
      exo.originalManifest.metadata.title
    )

    assertEquals(
      9,
      exo.readingOrderItems.size
    )

    assertEquals(
      "Part 1, Sections 1 - 3",
      exo.toc.tocItemsInOrder[0].title
    )
    assertEquals(
      "Part 1, Sections 4 - 5",
      exo.toc.tocItemsInOrder[1].title
    )
    assertEquals(
      "Part 1, Sections 6 - 7",
      exo.toc.tocItemsInOrder[2].title
    )
    assertEquals(
      "Part 1, Sections 8 - 10",
      exo.toc.tocItemsInOrder[3].title
    )
    assertEquals(
      "Part 1, Sections 11 - 12",
      exo.toc.tocItemsInOrder[4].title
    )
    assertEquals(
      "Part 2, Sections 13 - 14",
      exo.toc.tocItemsInOrder[5].title
    )
    assertEquals(
      "Part 2, Sections 15 - 17",
      exo.toc.tocItemsInOrder[6].title
    )
    assertEquals(
      "Part 2, Sections 18 - 20",
      exo.toc.tocItemsInOrder[7].title
    )
    assertEquals(
      "Part 2, Sections 21 - 22",
      exo.toc.tocItemsInOrder[8].title
    )

    assertEquals(
      "audio/mpeg",
      exo.readingOrderItems[0].item.link.type!!.fullType
    )
    assertEquals(
      "audio/mpeg",
      exo.readingOrderItems[1].item.link.type!!.fullType
    )
    assertEquals(
      "audio/mpeg",
      exo.readingOrderItems[2].item.link.type!!.fullType
    )
    assertEquals(
      "audio/mpeg",
      exo.readingOrderItems[3].item.link.type!!.fullType
    )
    assertEquals(
      "audio/mpeg",
      exo.readingOrderItems[4].item.link.type!!.fullType
    )
    assertEquals(
      "audio/mpeg",
      exo.readingOrderItems[5].item.link.type!!.fullType
    )
    assertEquals(
      "audio/mpeg",
      exo.readingOrderItems[6].item.link.type!!.fullType
    )
    assertEquals(
      "audio/mpeg",
      exo.readingOrderItems[7].item.link.type!!.fullType
    )
    assertEquals(
      "audio/mpeg",
      exo.readingOrderItems[8].item.link.type!!.fullType
    )

    assertEquals(
      "PT1371S",
      exo.toc.tocItemsInOrder[0].duration.toString()
    )
    assertEquals(
      "PT1669S",
      exo.toc.tocItemsInOrder[1].duration.toString()
    )
    assertEquals(
      "PT1506S",
      exo.toc.tocItemsInOrder[2].duration.toString()
    )
    assertEquals(
      "PT1798S",
      exo.toc.tocItemsInOrder[3].duration.toString()
    )
    assertEquals(
      "PT1225S",
      exo.toc.tocItemsInOrder[4].duration.toString()
    )
    assertEquals(
      "PT1659S",
      exo.toc.tocItemsInOrder[5].duration.toString()
    )
    assertEquals(
      "PT2086S",
      exo.toc.tocItemsInOrder[6].duration.toString()
    )
    assertEquals(
      "PT2662S",
      exo.toc.tocItemsInOrder[7].duration.toString()
    )
    assertEquals(
      "PT1177S",
      exo.toc.tocItemsInOrder[8].duration.toString()
    )

    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_1_abbott.mp3",
      exo.readingOrderItems[0].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_2_abbott.mp3",
      exo.readingOrderItems[1].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_3_abbott.mp3",
      exo.readingOrderItems[2].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_4_abbott.mp3",
      exo.readingOrderItems[3].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_5_abbott.mp3",
      exo.readingOrderItems[4].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_6_abbott.mp3",
      exo.readingOrderItems[5].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_7_abbott.mp3",
      exo.readingOrderItems[6].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_8_abbott.mp3",
      exo.readingOrderItems[7].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_9_abbott.mp3",
      exo.readingOrderItems[8].item.link.hrefURI!!.toString()
    )

    assertEquals(
      "0",
      exo.readingOrderItems[0].index.toString()
    )
    assertEquals(
      "1",
      exo.readingOrderItems[1].index.toString()
    )
    assertEquals(
      "2",
      exo.readingOrderItems[2].index.toString()
    )
    assertEquals(
      "3",
      exo.readingOrderItems[3].index.toString()
    )
    assertEquals(
      "4",
      exo.readingOrderItems[4].index.toString()
    )
    assertEquals(
      "5",
      exo.readingOrderItems[5].index.toString()
    )
    assertEquals(
      "6",
      exo.readingOrderItems[6].index.toString()
    )
    assertEquals(
      "7",
      exo.readingOrderItems[7].index.toString()
    )
    assertEquals(
      "8",
      exo.readingOrderItems[8].index.toString()
    )
  }

  @Test
  fun testOkBestNewHorror() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:flatland"),
        input = resource("bestnewhorror.audiobook-manifest.json"),
        extensions = listOf()
      )

    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result

    val exoResult =
      ExoManifest.transform(
        bookID = PlayerBookID.transform("x"),
        manifest = manifest,
        missingTrackNames = this.missingNames
      )
    dumpResult(exoResult)
    assertTrue(exoResult is PlayerResult.Success, "Result is success")

    val exoSuccess: PlayerResult.Success<ExoManifest, Exception> =
      exoResult as PlayerResult.Success<ExoManifest, Exception>

    val exo = exoSuccess.result

    assertEquals(
      7,
      exo.readingOrderItems.size
    )
    assertEquals(
      7,
      exo.toc.tocItemsInOrder.size
    )

    assertEquals(
      "Track 1",
      exo.toc.tocItemsInOrder[0].title
    )
    assertEquals(
      "Chapter 2",
      exo.toc.tocItemsInOrder[1].title
    )
    assertEquals(
      "Chapter 3",
      exo.toc.tocItemsInOrder[2].title
    )
    assertEquals(
      "Chapter 4",
      exo.toc.tocItemsInOrder[3].title
    )
    assertEquals(
      "Chapter 5",
      exo.toc.tocItemsInOrder[4].title
    )
    assertEquals(
      "Chapter 6",
      exo.toc.tocItemsInOrder[5].title
    )
    assertEquals(
      "Chapter 7",
      exo.toc.tocItemsInOrder[6].title
    )

    assertEquals(
      "PT487S",
      exo.toc.tocItemsInOrder[0].duration.toString()
    )
    assertEquals(
      "PT437S",
      exo.toc.tocItemsInOrder[1].duration.toString()
    )
    assertEquals(
      "PT364S",
      exo.toc.tocItemsInOrder[2].duration.toString()
    )
    assertEquals(
      "PT299S",
      exo.toc.tocItemsInOrder[3].duration.toString()
    )
    assertEquals(
      "PT668S",
      exo.toc.tocItemsInOrder[4].duration.toString()
    )
    assertEquals(
      "PT626S",
      exo.toc.tocItemsInOrder[5].duration.toString()
    )
    assertEquals(
      "PT539S",
      exo.toc.tocItemsInOrder[6].duration.toString()
    )

    assertEquals(
      "ee2176d6-c36f-4215-9365-b305514acd64.MP3.mp3",
      exo.readingOrderItems[0].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "75272c5c-28c0-4603-9f30-967167072575.MP3.mp3",
      exo.readingOrderItems[1].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "d9630648-ee40-40b6-93dc-0681e1dbfc5b.MP3.mp3",
      exo.readingOrderItems[2].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "cca56d65-bd7f-48ff-9606-3c2afae5b94f.MP3.mp3",
      exo.readingOrderItems[3].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "bcfc1bfa-c6f6-4bc4-a3bd-3bd489fb40c4.MP3.mp3",
      exo.readingOrderItems[4].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "a0ca6922-019f-4dc4-9103-2f78ae4e9bf4.MP3.mp3",
      exo.readingOrderItems[5].item.link.hrefURI!!.toString()
    )
    assertEquals(
      "0079b4de-6bd1-43d5-a082-afa89134957c.MP3.mp3",
      exo.readingOrderItems[6].item.link.hrefURI!!.toString()
    )
  }

  @Test
  fun testOkFlatlandTOC() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:flatland"),
        input = resource("flatland_toc.audiobook-manifest.json"),
        extensions = listOf()
      )

    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result

    val exoResult =
      ExoManifest.transform(
        bookID = PlayerBookID.transform("x"),
        manifest = manifest,
        missingTrackNames = this.missingNames
      )
    dumpResult(exoResult)
    assertTrue(exoResult is PlayerResult.Success, "Result is success")

    val exoSuccess: PlayerResult.Success<ExoManifest, Exception> =
      exoResult as PlayerResult.Success<ExoManifest, Exception>

    val exo = exoSuccess.result

    assertEquals(
      "Flatland",
      exo.originalManifest.metadata.title
    )

    assertEquals(
      manifest.readingOrder.size,
      exo.readingOrderItems.size
    )

    assertEquals(
      23,
      exo.toc.tocItemsInOrder.size
    )

    val titles = listOf(
      "Part 1 - This World",
      "Section 1 - Of the Nature of Flatland",
      "Section 2 - Of the Climate and Houses in Flatland",
      "Section 3 - Concerning the Inhabitants of Flatland",
      "Section 4 - Concerning the Women",
      "Section 5 - Of our Methods of Recognizing one another",
      "Section 6 - Of Recognition by Sight",
      "Section 7 - Concerning Irregular Figures",
      "Section 8 - Of the Ancient Practice of Painting",
      "Section 9 - Of the Universal Colour Bill",
      "Section 10 - Of the Suppression of the Chromatic Sedition",
      "Section 11 - Concerning our Priests",
      "Section 12 - Of the Doctrine of our Priests",
      "Part 2 - Other Worlds",
      "Section 13 - How I had a Vision of Lineland",
      "Section 14 - How I vainly tried to explain the nature of Flatland",
      // There is a zero-length section in the middle of the book
      // "Section 15 - Concerning a Stranger from Spaceland",
      "Section 16 - How the Stranger vainly endeavoured to reveal to me in words the mysteries of Spaceland",
      "Section 17 - How the Sphere, having in vain tried words, resorted to deeds",
      "Section 18 - How I came to Spaceland, and what I saw there",
      "Section 19 - How, though the Sphere shewed me other mysteries of Spaceland, I still desire more; and what came of it",
      "Section 20 - How the Sphere encouraged me in a Vision",
      "Section 21 - How I tried to teach the Theory of Three Dimensions to my Grandson, and with what success",
      "Section 22 - How I then tried to diffuse the Theory of Three Dimensions by other means, and of the result"
    )

    val durations = listOf(
      9.0,
      335.0,
      374.0,
      600.0,
      864.0,
      804.0,
      931.0,
      575.0,
      448.0,
      659.0,
      691.0,
      435.0,
      790.0,
      8.0,
      777.0,
      1421.0,
      // Zero-length TOC items are not allowed.
      // 0.0,
      1164.0,
      374.0,
      965.0,
      1117.0,
      582.0,
      437.0,
      722.0
    )

    exo.toc.tocItemsInOrder.forEachIndexed { index, tocItem ->
      log().debug("[{}] {} {}", index, tocItem.duration, tocItem.title)
    }

    exo.toc.tocItemsInOrder.forEachIndexed { index, tocItem ->
      assertEquals(
        titles[index],
        tocItem.title,
        "[$index] Title ${titles[index]} == ${tocItem.title}"
      )
      assertEquals(
        durations[index].toLong(),
        tocItem.duration.standardSeconds,
        "[$index] Duration ${durations[index].toLong()} == ${tocItem.duration.standardSeconds}"
      )
    }
  }

  @Test
  fun testOkAnnaKareninaTOC() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:anna_karenina"),
        input = resource("anna_karenina_toc.audiobook-manifest.json"),
        extensions = listOf()
      )

    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result

    val exoResult =
      ExoManifest.transform(
        bookID = PlayerBookID.transform("x"),
        manifest = manifest,
        missingTrackNames = this.missingNames
      )
    dumpResult(exoResult)
    assertTrue(exoResult is PlayerResult.Success, "Result is success")

    exoResult as PlayerResult.Success<ExoManifest, Exception>

    assertNotEquals(
      manifest.readingOrder.size,
      manifest.toc?.size
    )

    assertEquals(
      manifest.readingOrder.size,
      239
    )

    assertEquals(
      manifest.toc?.size,
      247
    )
  }

  private fun dumpResult(
    exoResult: PlayerResult<ExoManifest, Exception>
  ) {
    val logger = this.log()

    when (exoResult) {
      is PlayerResult.Failure -> {
        logger.debug("exoResult: FAILURE: ", exoResult.failure)
      }

      is PlayerResult.Success -> {
        logger.debug("exoResult: SUCCESS: {}", exoResult.result)
      }
    }
  }

  private fun resource(name: String): ManifestUnparsed {
    val path = "/org/librarysimplified/audiobook/tests/" + name
    return ManifestUnparsed(
      palaceId = PlayerPalaceID(path),
      data = ResourceMarker::class.java.getResourceAsStream(path)?.readBytes()
        ?: throw AssertionError("Missing resource file: " + path)
    )
  }

  /**
   * "This one is an example of a manifest with an implicit “introduction“ or “forward” chapter.
   * The “9781442304611_00_TheDemonsCovenant_Title.mp3“ file from the ReadingOrder is never
   * referenced in the TOC."
   */

  @Test
  fun testDifficultManifest0() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:demon"),
        input = resource("audible/demon.json"),
        extensions = listOf()
      )

    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest =
      success.result

    (ExoManifest.transform(
      bookID = PlayerBookID.transform("x"),
      manifest = manifest,
      missingTrackNames = this.missingNames
    ) as PlayerResult.Success).result
  }
}
