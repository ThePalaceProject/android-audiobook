package org.librarysimplified.audiobook.tests

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.feedbooks.FeedbooksRights
import org.librarysimplified.audiobook.feedbooks.FeedbooksSignature
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestScalar
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.slf4j.Logger
import java.net.URI
import java.util.ServiceLoader

/**
 * Tests for the {@link org.librarysimplified.audiobook.api.PlayerRawManifest} type.
 */

abstract class PlayerManifestContract {

  abstract fun log(): Logger

  @Test
  fun testEmptyManifest() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:empty"),
        streams = ByteArray(0),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Failure, "Result is failure")
  }

  @Test
  fun testErrorMinimal0() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:minimal"),
        streams = this.resource("error_minimal_0.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Failure, "Result is failure")
  }

  @Test
  fun testOkMinimal0() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:minimal"),
        streams = this.resource("ok_minimal_0.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkMinimalValues(manifest)
  }

  @Test
  fun testOkMinimal0WithExtensions() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:minimal"),
        streams = this.resource("ok_minimal_0.json"),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkMinimalValues(manifest)
  }

  private fun checkMinimalValues(manifest: PlayerManifest) {
    Assertions.assertEquals(3, manifest.readingOrder.size)
    Assertions.assertEquals("Track 0", manifest.readingOrder[0].title.toString())
    Assertions.assertEquals("100.0", manifest.readingOrder[0].duration.toString())
    Assertions.assertEquals("audio/mpeg", manifest.readingOrder[0].type.toString())
    Assertions.assertEquals("http://www.example.com/0.mp3", manifest.readingOrder[0].hrefURI.toString())

    Assertions.assertEquals("Track 1", manifest.readingOrder[1].title.toString())
    Assertions.assertEquals("200.0", manifest.readingOrder[1].duration.toString())
    Assertions.assertEquals("audio/mpeg", manifest.readingOrder[1].type.toString())
    Assertions.assertEquals("http://www.example.com/1.mp3", manifest.readingOrder[1].hrefURI.toString())

    Assertions.assertEquals("Track 2", manifest.readingOrder[2].title.toString())
    Assertions.assertEquals("300.0", manifest.readingOrder[2].duration.toString())
    Assertions.assertEquals("audio/mpeg", manifest.readingOrder[2].type.toString())
    Assertions.assertEquals("http://www.example.com/2.mp3", manifest.readingOrder[2].hrefURI.toString())

    Assertions.assertEquals("title", manifest.metadata.title)
    Assertions.assertEquals("urn:id", manifest.metadata.identifier)
  }

  @Test
  fun testOkNullTitles() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("nulltitles"),
        streams = this.resource("null_titles.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkNullTitleValues(manifest)
  }

  private fun checkNullTitleValues(manifest: PlayerManifest) {
    Assertions.assertEquals(2, manifest.readingOrder.size)

    // null title should be null
    Assertions.assertNull(manifest.readingOrder[0].title)

    // no title should be null
    Assertions.assertNull(manifest.readingOrder[1].title)
  }

  @Test
  fun testOkNullLinkType() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("null_link_type"),
        streams = this.resource("null_link_type.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkNullLinkTypeValues(manifest)
  }

  private fun checkNullLinkTypeValues(manifest: PlayerManifest) {
    Assertions.assertEquals(2, manifest.links.size)

    // null type should be null
    Assertions.assertNull(manifest.links[0].type)

    // no type should be null
    Assertions.assertNull(manifest.links[1].type)
  }

  @Test
  fun testOkFlatlandGardeur() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("flatland"),
        streams = this.resource("flatland.audiobook-manifest.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkFlatlandValues(manifest)
  }

  @Test
  fun testOkFlatlandGardeurWithExtensions() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("flatland"),
        streams = this.resource("flatland.audiobook-manifest.json"),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkFlatlandValues(manifest)
  }

  private fun checkFlatlandValues(manifest: PlayerManifest) {
    Assertions.assertEquals(
      "Flatland: A Romance of Many Dimensions",
      manifest.metadata.title
    )
    Assertions.assertEquals(
      "https://librivox.org/flatland-a-romance-of-many-dimensions-by-edwin-abbott-abbott/",
      manifest.metadata.identifier
    )

    Assertions.assertEquals(
      9,
      manifest.readingOrder.size
    )

    Assertions.assertEquals(
      "Part 1, Sections 1 - 3",
      manifest.readingOrder[0].title.toString()
    )
    Assertions.assertEquals(
      "Part 1, Sections 4 - 5",
      manifest.readingOrder[1].title.toString()
    )
    Assertions.assertEquals(
      "Part 1, Sections 6 - 7",
      manifest.readingOrder[2].title.toString()
    )
    Assertions.assertEquals(
      "Part 1, Sections 8 - 10",
      manifest.readingOrder[3].title.toString()
    )
    Assertions.assertEquals(
      "Part 1, Sections 11 - 12",
      manifest.readingOrder[4].title.toString()
    )
    Assertions.assertEquals(
      "Part 2, Sections 13 - 14",
      manifest.readingOrder[5].title.toString()
    )
    Assertions.assertEquals(
      "Part 2, Sections 15 - 17",
      manifest.readingOrder[6].title.toString()
    )
    Assertions.assertEquals(
      "Part 2, Sections 18 - 20",
      manifest.readingOrder[7].title.toString()
    )
    Assertions.assertEquals(
      "Part 2, Sections 21 - 22",
      manifest.readingOrder[8].title.toString()
    )

    Assertions.assertEquals(
      "audio/mpeg",
      manifest.readingOrder[0].type.toString()
    )
    Assertions.assertEquals(
      "audio/mpeg",
      manifest.readingOrder[1].type.toString()
    )
    Assertions.assertEquals(
      "audio/mpeg",
      manifest.readingOrder[2].type.toString()
    )
    Assertions.assertEquals(
      "audio/mpeg",
      manifest.readingOrder[3].type.toString()
    )
    Assertions.assertEquals(
      "audio/mpeg",
      manifest.readingOrder[4].type.toString()
    )
    Assertions.assertEquals(
      "audio/mpeg",
      manifest.readingOrder[5].type.toString()
    )
    Assertions.assertEquals(
      "audio/mpeg",
      manifest.readingOrder[6].type.toString()
    )
    Assertions.assertEquals(
      "audio/mpeg",
      manifest.readingOrder[7].type.toString()
    )
    Assertions.assertEquals(
      "audio/mpeg",
      manifest.readingOrder[8].type.toString()
    )

    Assertions.assertEquals(
      "1371.0",
      manifest.readingOrder[0].duration.toString()
    )
    Assertions.assertEquals(
      "1669.0",
      manifest.readingOrder[1].duration.toString()
    )
    Assertions.assertEquals(
      "1506.0",
      manifest.readingOrder[2].duration.toString()
    )
    Assertions.assertEquals(
      "1798.0",
      manifest.readingOrder[3].duration.toString()
    )
    Assertions.assertEquals(
      "1225.0",
      manifest.readingOrder[4].duration.toString()
    )
    Assertions.assertEquals(
      "1659.0",
      manifest.readingOrder[5].duration.toString()
    )
    Assertions.assertEquals(
      "2086.0",
      manifest.readingOrder[6].duration.toString()
    )
    Assertions.assertEquals(
      "2662.0",
      manifest.readingOrder[7].duration.toString()
    )
    Assertions.assertEquals(
      "1177.0",
      manifest.readingOrder[8].duration.toString()
    )

    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_1_abbott.mp3",
      manifest.readingOrder[0].hrefURI.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_2_abbott.mp3",
      manifest.readingOrder[1].hrefURI.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_3_abbott.mp3",
      manifest.readingOrder[2].hrefURI.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_4_abbott.mp3",
      manifest.readingOrder[3].hrefURI.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_5_abbott.mp3",
      manifest.readingOrder[4].hrefURI.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_6_abbott.mp3",
      manifest.readingOrder[5].hrefURI.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_7_abbott.mp3",
      manifest.readingOrder[6].hrefURI.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_8_abbott.mp3",
      manifest.readingOrder[7].hrefURI.toString()
    )
    Assertions.assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_9_abbott.mp3",
      manifest.readingOrder[8].hrefURI.toString()
    )
  }

  @Test
  fun testOkFeedbooks0() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("feedbooks"),
        streams = this.resource("feedbooks_0.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkFeedbooks0Values(manifest)
  }

  @Test
  fun testOkFeedbooks0WithExtensions() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("feedbooks"),
        streams = this.resource("feedbooks_0.json"),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkFeedbooks0Values(manifest)

    val extensions = manifest.extensions

    this.run {
      val sig = extensions[0] as FeedbooksSignature
      Assertions.assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", sig.algorithm)
      Assertions.assertEquals("https://www.cantookaudio.com", sig.issuer)
      Assertions.assertEquals(
        "eKLux/4TtJc6VH6RTOi5lBMh9mT1j2y1z50OruWZgy8QjyPMjDV+aVZWUt7OUTinUHQfWNPBB6DxixgTZ07TQsix4uScL2dJZRQTjUKKHv3he7oJdOkcxjWDh51Q6U2KbDfC2MReG/+Qa4meoI5BN0Q8FKIEFMDZJ2KQTSRj13ZETaD0Nwz+8d6IN7csQGFJHvW/bBJthty+eZNzIr+VE0Kf02OS4yX+wvsExfRabvHlfimT1uUTWc89CgPAuM+Y7vdtjb+B3YFr7ibXATk6lQJkXzKol9ms6vkNwnvxzXwsQ+p1ZjejH1LOYADvedl/ItPrBGkhmq7bbUz91jUd+w==",
        sig.value
      )
    }

    this.run {
      val rights = extensions[1] as FeedbooksRights
      Assertions.assertEquals("2020-02-01T17:15:52.000", rights.validStart.toString())
      Assertions.assertEquals("2020-03-29T17:15:52.000", rights.validEnd.toString())
    }
  }

  private fun checkFeedbooks0Values(manifest: PlayerManifest) {
    Assertions.assertEquals(
      "http://archive.org/details/gleams_of_sunshine_1607_librivox",
      manifest.metadata.identifier
    )
    Assertions.assertEquals(
      "Gleams of Sunshine",
      manifest.metadata.title
    )

    Assertions.assertEquals(1, manifest.readingOrder.size)

    this.run {
      Assertions.assertEquals(
        128.0,
        manifest.readingOrder[0].duration
      )
      Assertions.assertEquals(
        "01 - Invocation",
        manifest.readingOrder[0].title
      )
      Assertions.assertEquals(
        120.0,
        manifest.readingOrder[0].bitrate
      )
      Assertions.assertEquals(
        "audio/mpeg",
        manifest.readingOrder[0].type?.fullType
      )
      Assertions.assertEquals(
        "http://archive.org/download/gleams_of_sunshine_1607_librivox/gleamsofsunshine_01_chant.mp3",
        manifest.readingOrder[0].hrefURI.toString()
      )

      val encrypted0 = manifest.readingOrder[0].properties.encrypted!!
      Assertions.assertEquals(
        "http://www.feedbooks.com/audiobooks/access-restriction",
        encrypted0.scheme
      )
      Assertions.assertEquals(
        "https://www.cantookaudio.com",
        (encrypted0.values["profile"] as PlayerManifestScalar.PlayerManifestScalarString).text
      )
    }

    Assertions.assertEquals(3, manifest.links.size)
    Assertions.assertEquals(
      "cover",
      manifest.links[0].relation[0]
    )
    Assertions.assertEquals(
      180,
      manifest.links[0].width
    )
    Assertions.assertEquals(
      180,
      manifest.links[0].height
    )
    Assertions.assertEquals(
      "image/jpeg",
      manifest.links[0].type?.fullType
    )
    Assertions.assertEquals(
      "http://archive.org/services/img/gleams_of_sunshine_1607_librivox",
      manifest.links[0].hrefURI.toString()
    )

    Assertions.assertEquals(
      "self",
      manifest.links[1].relation[0]
    )
    Assertions.assertEquals(
      "application/audiobook+json",
      manifest.links[1].type?.fullType
    )
    Assertions.assertEquals(
      "https://api.archivelab.org/books/gleams_of_sunshine_1607_librivox/opds_audio_manifest",
      manifest.links[1].hrefURI.toString()
    )

    Assertions.assertEquals(
      "license",
      manifest.links[2].relation[0]
    )
    Assertions.assertEquals(
      "application/vnd.readium.license.status.v1.0+json",
      manifest.links[2].type?.fullType
    )
    Assertions.assertEquals(
      "http://example.com/license/status",
      manifest.links[2].hrefURI.toString()
    )
  }

  @Test
  fun testOkFindaway0() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("findaway"),
        streams = this.resource("findaway.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result

    Assertions.assertEquals(
      "Most Dangerous",
      manifest.metadata.title
    )
    Assertions.assertEquals(
      "urn:librarysimplified.org/terms/id/Bibliotheca%20ID/hxaee89",
      manifest.metadata.identifier
    )

    val encrypted = manifest.metadata.encrypted!!
    Assertions.assertEquals(
      "http://librarysimplified.org/terms/drm/scheme/FAE",
      encrypted.scheme
    )

    Assertions.assertEquals(
      "REDACTED0",
      encrypted.values["findaway:accountId"].toString()
    )
    Assertions.assertEquals(
      "REDACTED1",
      encrypted.values["findaway:checkoutId"].toString()
    )
    Assertions.assertEquals(
      "REDACTED2",
      encrypted.values["findaway:sessionKey"].toString()
    )
    Assertions.assertEquals(
      "REDACTED3",
      encrypted.values["findaway:fulfillmentId"].toString()
    )
    Assertions.assertEquals(
      "REDACTED4",
      encrypted.values["findaway:licenseId"].toString()
    )

    Assertions.assertEquals(
      "1",
      manifest.readingOrder[0].properties.extras["findaway:sequence"].toString()
    )
    Assertions.assertEquals(
      "0",
      manifest.readingOrder[0].properties.extras["findaway:part"].toString()
    )
  }

  @Test
  fun testOkFindaway20201015() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("findaway"),
        streams = this.resource("findaway-20201015.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result

    Assertions.assertEquals(
      "Man Riding West",
      manifest.metadata.title
    )
    Assertions.assertEquals(
      "urn:librarysimplified.org/terms/id/Bibliotheca%20ID/ebwowg9",
      manifest.metadata.identifier
    )

    val encrypted = manifest.metadata.encrypted!!
    Assertions.assertEquals(
      "http://librarysimplified.org/terms/drm/scheme/FAE",
      encrypted.scheme
    )

    Assertions.assertEquals(
      "REDACTED0",
      encrypted.values["findaway:accountId"].toString()
    )
    Assertions.assertEquals(
      "REDACTED1",
      encrypted.values["findaway:checkoutId"].toString()
    )
    Assertions.assertEquals(
      "REDACTED2",
      encrypted.values["findaway:sessionKey"].toString()
    )
    Assertions.assertEquals(
      "REDACTED3",
      encrypted.values["findaway:fulfillmentId"].toString()
    )
    Assertions.assertEquals(
      "REDACTED4",
      encrypted.values["findaway:licenseId"].toString()
    )

    Assertions.assertEquals(
      "1",
      manifest.readingOrder[0].properties.extras["findaway:sequence"].toString()
    )
    Assertions.assertEquals(
      "0",
      manifest.readingOrder[0].properties.extras["findaway:part"].toString()
    )
  }

  @Test
  fun testOkFindawayLeading0() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("findaway"),
        streams = this.resource("findaway_leading_0.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result

    val encrypted = manifest.metadata.encrypted!!

    Assertions.assertEquals(
      "012345",
      encrypted.values["findaway:fulfillmentId"].toString()
    )
  }

  @Test
  fun testOkFeedbooks1() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("feedbooks"),
        streams = this.resource("feedbooks_1.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkFeedbooks1Values(manifest)
  }

  @Test
  fun testOkFeedbooks1WithExtensions() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("feedbooks"),
        streams = this.resource("feedbooks_1.json"),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkFeedbooks1Values(manifest)
  }

  @Test
  fun testOkIGen() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("igen"),
        streams = this.resource("igen.json"),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    assertEquals("urn:isbn:9781508245063", manifest.metadata.identifier)

    for (item in manifest.readingOrder) {
      assertFalse(item.hrefURI!!.toString().startsWith("/"))
    }
  }

  private fun checkFeedbooks1Values(manifest: PlayerManifest) {
    Assertions.assertEquals(
      "urn:uuid:35c5e499-9cb9-46e0-9e47-c517973f9e7f",
      manifest.metadata.identifier
    )
    Assertions.assertEquals(
      "Rise of the Dragons, Book 1",
      manifest.metadata.title
    )

    /*
     * I don't think we really need to check the contents of all 41 spine items.
     * The rest of the test suite should hopefully cover this sufficiently.
     */

    Assertions.assertEquals(41, manifest.readingOrder.size)
    Assertions.assertEquals(3, manifest.links.size)
  }

  private fun resource(name: String): ByteArray {
    val path = "/org/librarysimplified/audiobook/tests/" + name
    return PlayerManifestContract::class.java.getResourceAsStream(path)?.readBytes()
      ?: throw AssertionError("Missing resource file: " + path)
  }
}
