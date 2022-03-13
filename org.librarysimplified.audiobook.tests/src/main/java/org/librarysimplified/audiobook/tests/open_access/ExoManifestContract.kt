package org.librarysimplified.audiobook.tests.open_access

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.open_access.ExoManifest
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.slf4j.Logger
import java.net.URI

/**
 * Tests for the {@link org.librarysimplified.audiobook.api.PlayerRawManifest} type.
 */

abstract class ExoManifestContract {

  abstract fun log(): Logger

  @Test
  fun testOkFlatlandGardeur() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:flatland"),
        streams = resource("flatland.audiobook-manifest.json"),
        extensions = listOf()
      )

    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result

    val exo_result = ExoManifest.transform(manifest)
    this.log().debug("exo_result: {}", exo_result)
    assertTrue(exo_result is PlayerResult.Success, "Result is success")

    val exo_success: PlayerResult.Success<ExoManifest, Exception> =
      exo_result as PlayerResult.Success<ExoManifest, Exception>

    val exo = exo_success.result

    Assertions.assertEquals(
      "Flatland: A Romance of Many Dimensions",
      exo.title
    )
    Assertions.assertEquals(
      "https://librivox.org/flatland-a-romance-of-many-dimensions-by-edwin-abbott-abbott/",
      exo.id
    )

    Assertions.assertEquals(
      9,
      exo.spineItems.size
    )

    Assertions.assertEquals(
      "Part 1, Sections 1 - 3",
      exo.spineItems[0].title
    )
    Assertions.assertEquals(
      "Part 1, Sections 4 - 5",
      exo.spineItems[1].title
    )
    Assertions.assertEquals(
      "Part 1, Sections 6 - 7",
      exo.spineItems[2].title
    )
    Assertions.assertEquals(
      "Part 1, Sections 8 - 10",
      exo.spineItems[3].title
    )
    Assertions.assertEquals(
      "Part 1, Sections 11 - 12",
      exo.spineItems[4].title
    )
    Assertions.assertEquals(
      "Part 2, Sections 13 - 14",
      exo.spineItems[5].title
    )
    Assertions.assertEquals(
      "Part 2, Sections 15 - 17",
      exo.spineItems[6].title
    )
    Assertions.assertEquals(
      "Part 2, Sections 18 - 20",
      exo.spineItems[7].title
    )
    Assertions.assertEquals(
      "Part 2, Sections 21 - 22",
      exo.spineItems[8].title
    )

    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[0].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[1].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[2].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[3].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[4].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[5].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[6].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[7].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[8].type.fullType
    )

    Assertions.assertEquals(
      "1371.0",
      exo.spineItems[0].duration.toString()
    )
    Assertions.assertEquals(
      "1669.0",
      exo.spineItems[1].duration.toString()
    )
    Assertions.assertEquals(
      "1506.0",
      exo.spineItems[2].duration.toString()
    )
    Assertions.assertEquals(
      "1798.0",
      exo.spineItems[3].duration.toString()
    )
    Assertions.assertEquals(
      "1225.0",
      exo.spineItems[4].duration.toString()
    )
    Assertions.assertEquals(
      "1659.0",
      exo.spineItems[5].duration.toString()
    )
    Assertions.assertEquals(
      "2086.0",
      exo.spineItems[6].duration.toString()
    )
    Assertions.assertEquals(
      "2662.0",
      exo.spineItems[7].duration.toString()
    )
    Assertions.assertEquals(
      "1177.0",
      exo.spineItems[8].duration.toString()
    )

    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_1_abbott.mp3",
      exo.spineItems[0].uri.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_2_abbott.mp3",
      exo.spineItems[1].uri.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_3_abbott.mp3",
      exo.spineItems[2].uri.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_4_abbott.mp3",
      exo.spineItems[3].uri.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_5_abbott.mp3",
      exo.spineItems[4].uri.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_6_abbott.mp3",
      exo.spineItems[5].uri.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_7_abbott.mp3",
      exo.spineItems[6].uri.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_8_abbott.mp3",
      exo.spineItems[7].uri.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_9_abbott.mp3",
      exo.spineItems[8].uri.toString()
    )

    Assertions.assertEquals(
      "0",
      exo.spineItems[0].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[1].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[2].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[3].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[4].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[5].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[6].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[7].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[8].part.toString()
    )

    Assertions.assertEquals(
      "0",
      exo.spineItems[0].chapter.toString()
    )
    Assertions.assertEquals(
      "1",
      exo.spineItems[1].chapter.toString()
    )
    Assertions.assertEquals(
      "2",
      exo.spineItems[2].chapter.toString()
    )
    Assertions.assertEquals(
      "3",
      exo.spineItems[3].chapter.toString()
    )
    Assertions.assertEquals(
      "4",
      exo.spineItems[4].chapter.toString()
    )
    Assertions.assertEquals(
      "5",
      exo.spineItems[5].chapter.toString()
    )
    Assertions.assertEquals(
      "6",
      exo.spineItems[6].chapter.toString()
    )
    Assertions.assertEquals(
      "7",
      exo.spineItems[7].chapter.toString()
    )
    Assertions.assertEquals(
      "8",
      exo.spineItems[8].chapter.toString()
    )
  }

  @Test
  fun testOkBestNewHorror() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:flatland"),
        streams = resource("bestnewhorror.audiobook-manifest.json"),
        extensions = listOf()
      )

    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result

    val exo_result = ExoManifest.transform(manifest)
    this.log().debug("exo_result: {}", exo_result)
    assertTrue(exo_result is PlayerResult.Success, "Result is success")

    val exo_success: PlayerResult.Success<ExoManifest, Exception> =
      exo_result as PlayerResult.Success<ExoManifest, Exception>

    val exo = exo_success.result

    Assertions.assertEquals(
      "Best New Horror",
      exo.title
    )
    Assertions.assertEquals(
      "urn:isbn:9780061552137",
      exo.id
    )

    Assertions.assertEquals(
      7,
      exo.spineItems.size
    )

    Assertions.assertEquals(
      "9780061552137_001_MP3.mp3",
      exo.spineItems[0].title
    )
    Assertions.assertEquals(
      "9780061552137_002_MP3.mp3",
      exo.spineItems[1].title
    )
    Assertions.assertEquals(
      "9780061552137_003_MP3.mp3",
      exo.spineItems[2].title
    )
    Assertions.assertEquals(
      "9780061552137_004_MP3.mp3",
      exo.spineItems[3].title
    )
    Assertions.assertEquals(
      "9780061552137_005_MP3.mp3",
      exo.spineItems[4].title
    )
    Assertions.assertEquals(
      "9780061552137_006_MP3.mp3",
      exo.spineItems[5].title
    )
    Assertions.assertEquals(
      "9780061552137_007_MP3.mp3",
      exo.spineItems[6].title
    )

    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[0].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[1].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[2].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[3].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[4].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[5].type.fullType
    )
    Assertions.assertEquals(
      "audio/mpeg",
      exo.spineItems[6].type.fullType
    )

    Assertions.assertEquals(
      "487.0",
      exo.spineItems[0].duration.toString()
    )
    Assertions.assertEquals(
      "437.0",
      exo.spineItems[1].duration.toString()
    )
    Assertions.assertEquals(
      "364.0",
      exo.spineItems[2].duration.toString()
    )
    Assertions.assertEquals(
      "299.0",
      exo.spineItems[3].duration.toString()
    )
    Assertions.assertEquals(
      "668.0",
      exo.spineItems[4].duration.toString()
    )
    Assertions.assertEquals(
      "626.0",
      exo.spineItems[5].duration.toString()
    )
    Assertions.assertEquals(
      "539.0",
      exo.spineItems[6].duration.toString()
    )

    Assertions.assertEquals(
      "ee2176d6-c36f-4215-9365-b305514acd64.MP3.mp3",
      exo.spineItems[0].uri.toString()
    )
    Assertions.assertEquals(
      "75272c5c-28c0-4603-9f30-967167072575.MP3.mp3",
      exo.spineItems[1].uri.toString()
    )
    Assertions.assertEquals(
      "d9630648-ee40-40b6-93dc-0681e1dbfc5b.MP3.mp3",
      exo.spineItems[2].uri.toString()
    )
    Assertions.assertEquals(
      "cca56d65-bd7f-48ff-9606-3c2afae5b94f.MP3.mp3",
      exo.spineItems[3].uri.toString()
    )
    Assertions.assertEquals(
      "bcfc1bfa-c6f6-4bc4-a3bd-3bd489fb40c4.MP3.mp3",
      exo.spineItems[4].uri.toString()
    )
    Assertions.assertEquals(
      "a0ca6922-019f-4dc4-9103-2f78ae4e9bf4.MP3.mp3",
      exo.spineItems[5].uri.toString()
    )
    Assertions.assertEquals(
      "0079b4de-6bd1-43d5-a082-afa89134957c.MP3.mp3",
      exo.spineItems[6].uri.toString()
    )

    Assertions.assertEquals(
      "0",
      exo.spineItems[0].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[1].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[2].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[3].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[4].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[5].part.toString()
    )
    Assertions.assertEquals(
      "0",
      exo.spineItems[6].part.toString()
    )

    Assertions.assertEquals(
      "0",
      exo.spineItems[0].chapter.toString()
    )
    Assertions.assertEquals(
      "1",
      exo.spineItems[1].chapter.toString()
    )
    Assertions.assertEquals(
      "2",
      exo.spineItems[2].chapter.toString()
    )
    Assertions.assertEquals(
      "3",
      exo.spineItems[3].chapter.toString()
    )
    Assertions.assertEquals(
      "4",
      exo.spineItems[4].chapter.toString()
    )
    Assertions.assertEquals(
      "5",
      exo.spineItems[5].chapter.toString()
    )
    Assertions.assertEquals(
      "6",
      exo.spineItems[6].chapter.toString()
    )

    Assertions.assertEquals(
      "http://readium.org/2014/01/lcp",
      exo.spineItems[0].originalLink.properties.encrypted?.scheme
    )
    Assertions.assertEquals(
      "http://readium.org/2014/01/lcp",
      exo.spineItems[1].originalLink.properties.encrypted?.scheme
    )
    Assertions.assertEquals(
      "http://readium.org/2014/01/lcp",
      exo.spineItems[2].originalLink.properties.encrypted?.scheme
    )
    Assertions.assertEquals(
      "http://readium.org/2014/01/lcp",
      exo.spineItems[3].originalLink.properties.encrypted?.scheme
    )
    Assertions.assertEquals(
      "http://readium.org/2014/01/lcp",
      exo.spineItems[4].originalLink.properties.encrypted?.scheme
    )
    Assertions.assertEquals(
      "http://readium.org/2014/01/lcp",
      exo.spineItems[5].originalLink.properties.encrypted?.scheme
    )
    Assertions.assertEquals(
      "http://readium.org/2014/01/lcp",
      exo.spineItems[6].originalLink.properties.encrypted?.scheme
    )
  }

  private fun resource(name: String): ByteArray {
    val path = "/org/librarysimplified/audiobook/tests/" + name
    return ExoManifestContract::class.java.getResourceAsStream(path)?.readBytes()
      ?: throw AssertionError("Missing resource file: " + path)
  }
}
