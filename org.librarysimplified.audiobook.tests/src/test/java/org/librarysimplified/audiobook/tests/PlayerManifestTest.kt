package org.librarysimplified.audiobook.tests

import org.joda.time.Duration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.feedbooks.FeedbooksRights
import org.librarysimplified.audiobook.feedbooks.FeedbooksSignature
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestScalar
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOC
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCs
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.manifest_parser.api.ManifestUnparsed
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.media3.ExoLCP
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.ServiceLoader

/**
 * Tests for the {@link org.librarysimplified.audiobook.api.PlayerRawManifest} type.
 */

class PlayerManifestTest {

  fun log(): Logger = LoggerFactory.getLogger(PlayerManifestTest::class.java)

  @Test
  fun testEmptyManifest() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:empty"),
        input = ManifestUnparsed(PlayerPalaceID("x"), ByteArray(0)),
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
        input = this.resource("error_minimal_0.json"),
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
        input = this.resource("ok_minimal_0.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkMinimalValues(manifest)

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  @Test
  fun testOkMinimal0WithExtensions() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:minimal"),
        input = this.resource("ok_minimal_0.json"),
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
    assertEquals(3, manifest.readingOrder.size)
    assertEquals("Track 0", manifest.readingOrder[0].link.title.toString())
    assertEquals("100.0", manifest.readingOrder[0].link.duration.toString())
    assertEquals("audio/mpeg", manifest.readingOrder[0].link.type.toString())
    assertEquals(
      "http://www.example.com/0.mp3",
      manifest.readingOrder[0].link.hrefURI.toString()
    )

    assertEquals("Track 1", manifest.readingOrder[1].link.title.toString())
    assertEquals("200.0", manifest.readingOrder[1].link.duration.toString())
    assertEquals("audio/mpeg", manifest.readingOrder[1].link.type.toString())
    assertEquals(
      "http://www.example.com/1.mp3",
      manifest.readingOrder[1].link.hrefURI.toString()
    )

    assertEquals("Track 2", manifest.readingOrder[2].link.title.toString())
    assertEquals("300.0", manifest.readingOrder[2].link.duration.toString())
    assertEquals("audio/mpeg", manifest.readingOrder[2].link.type.toString())
    assertEquals(
      "http://www.example.com/2.mp3",
      manifest.readingOrder[2].link.hrefURI.toString()
    )

    assertEquals("title", manifest.metadata.title)
    assertEquals("urn:id", manifest.metadata.identifier)

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  @Test
  fun testOkNullTitles() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("nulltitles"),
        input = this.resource("null_titles.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkNullTitleValues(manifest)

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  private fun checkNullTitleValues(manifest: PlayerManifest) {
    assertEquals(2, manifest.readingOrder.size)

    // null title should be null
    Assertions.assertNull(manifest.readingOrder[0].link.title)

    // no title should be null
    Assertions.assertNull(manifest.readingOrder[1].link.title)
  }

  @Test
  fun testOkNullLinkType() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("null_link_type"),
        input = this.resource("null_link_type.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkNullLinkTypeValues(manifest)

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  private fun checkNullLinkTypeValues(manifest: PlayerManifest) {
    assertEquals(2, manifest.links.size)

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
        input = this.resource("flatland.audiobook-manifest.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkFlatlandValues(manifest)

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  @Test
  fun testOkFlatlandGardeurWithExtensions() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("flatland"),
        input = this.resource("flatland.audiobook-manifest.json"),
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
    assertEquals(
      "Flatland: A Romance of Many Dimensions",
      manifest.metadata.title
    )
    assertEquals(
      "https://librivox.org/flatland-a-romance-of-many-dimensions-by-edwin-abbott-abbott/",
      manifest.metadata.identifier
    )

    assertEquals(
      9,
      manifest.readingOrder.size
    )

    assertEquals(
      "Part 1, Sections 1 - 3",
      manifest.readingOrder[0].link.title.toString()
    )
    assertEquals(
      "Part 1, Sections 4 - 5",
      manifest.readingOrder[1].link.title.toString()
    )
    assertEquals(
      "Part 1, Sections 6 - 7",
      manifest.readingOrder[2].link.title.toString()
    )
    assertEquals(
      "Part 1, Sections 8 - 10",
      manifest.readingOrder[3].link.title.toString()
    )
    assertEquals(
      "Part 1, Sections 11 - 12",
      manifest.readingOrder[4].link.title.toString()
    )
    assertEquals(
      "Part 2, Sections 13 - 14",
      manifest.readingOrder[5].link.title.toString()
    )
    assertEquals(
      "Part 2, Sections 15 - 17",
      manifest.readingOrder[6].link.title.toString()
    )
    assertEquals(
      "Part 2, Sections 18 - 20",
      manifest.readingOrder[7].link.title.toString()
    )
    assertEquals(
      "Part 2, Sections 21 - 22",
      manifest.readingOrder[8].link.title.toString()
    )

    assertEquals(
      "audio/mpeg",
      manifest.readingOrder[0].link.type.toString()
    )
    assertEquals(
      "audio/mpeg",
      manifest.readingOrder[1].link.type.toString()
    )
    assertEquals(
      "audio/mpeg",
      manifest.readingOrder[2].link.type.toString()
    )
    assertEquals(
      "audio/mpeg",
      manifest.readingOrder[3].link.type.toString()
    )
    assertEquals(
      "audio/mpeg",
      manifest.readingOrder[4].link.type.toString()
    )
    assertEquals(
      "audio/mpeg",
      manifest.readingOrder[5].link.type.toString()
    )
    assertEquals(
      "audio/mpeg",
      manifest.readingOrder[6].link.type.toString()
    )
    assertEquals(
      "audio/mpeg",
      manifest.readingOrder[7].link.type.toString()
    )
    assertEquals(
      "audio/mpeg",
      manifest.readingOrder[8].link.type.toString()
    )

    assertEquals(
      "1371.0",
      manifest.readingOrder[0].link.duration.toString()
    )
    assertEquals(
      "1669.0",
      manifest.readingOrder[1].link.duration.toString()
    )
    assertEquals(
      "1506.0",
      manifest.readingOrder[2].link.duration.toString()
    )
    assertEquals(
      "1798.0",
      manifest.readingOrder[3].link.duration.toString()
    )
    assertEquals(
      "1225.0",
      manifest.readingOrder[4].link.duration.toString()
    )
    assertEquals(
      "1659.0",
      manifest.readingOrder[5].link.duration.toString()
    )
    assertEquals(
      "2086.0",
      manifest.readingOrder[6].link.duration.toString()
    )
    assertEquals(
      "2662.0",
      manifest.readingOrder[7].link.duration.toString()
    )
    assertEquals(
      "1177.0",
      manifest.readingOrder[8].link.duration.toString()
    )

    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_1_abbott.mp3",
      manifest.readingOrder[0].link.hrefURI.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_2_abbott.mp3",
      manifest.readingOrder[1].link.hrefURI.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_3_abbott.mp3",
      manifest.readingOrder[2].link.hrefURI.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_4_abbott.mp3",
      manifest.readingOrder[3].link.hrefURI.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_5_abbott.mp3",
      manifest.readingOrder[4].link.hrefURI.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_6_abbott.mp3",
      manifest.readingOrder[5].link.hrefURI.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_7_abbott.mp3",
      manifest.readingOrder[6].link.hrefURI.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_8_abbott.mp3",
      manifest.readingOrder[7].link.hrefURI.toString()
    )
    assertEquals(
      "http://www.archive.org/download/flatland_rg_librivox/flatland_9_abbott.mp3",
      manifest.readingOrder[8].link.hrefURI.toString()
    )

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  @Test
  fun testOkFeedbooks0() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("feedbooks"),
        input = this.resource("feedbooks_0.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkFeedbooks0Values(manifest)

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  @Test
  fun testOkFeedbooks0WithExtensions() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("feedbooks"),
        input = this.resource("feedbooks_0.json"),
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
      assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", sig.algorithm)
      assertEquals("https://www.cantookaudio.com", sig.issuer)
      assertEquals(
        "eKLux/4TtJc6VH6RTOi5lBMh9mT1j2y1z50OruWZgy8QjyPMjDV+aVZWUt7OUTinUHQfWNPBB6DxixgTZ07TQsix4uScL2dJZRQTjUKKHv3he7oJdOkcxjWDh51Q6U2KbDfC2MReG/+Qa4meoI5BN0Q8FKIEFMDZJ2KQTSRj13ZETaD0Nwz+8d6IN7csQGFJHvW/bBJthty+eZNzIr+VE0Kf02OS4yX+wvsExfRabvHlfimT1uUTWc89CgPAuM+Y7vdtjb+B3YFr7ibXATk6lQJkXzKol9ms6vkNwnvxzXwsQ+p1ZjejH1LOYADvedl/ItPrBGkhmq7bbUz91jUd+w==",
        sig.value
      )
    }

    this.run {
      val rights = extensions[1] as FeedbooksRights
      assertEquals("2020-02-01T17:15:52.000", rights.validStart.toString())
      assertEquals("2020-03-29T17:15:52.000", rights.validEnd.toString())
    }

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  private fun checkFeedbooks0Values(manifest: PlayerManifest) {
    assertEquals(
      "http://archive.org/details/gleams_of_sunshine_1607_librivox",
      manifest.metadata.identifier
    )
    assertEquals(
      "Gleams of Sunshine",
      manifest.metadata.title
    )

    assertEquals(1, manifest.readingOrder.size)

    this.run {
      assertEquals(
        128.0,
        manifest.readingOrder[0].link.duration
      )
      assertEquals(
        "01 - Invocation",
        manifest.readingOrder[0].link.title
      )
      assertEquals(
        120.0,
        manifest.readingOrder[0].link.bitrate
      )
      assertEquals(
        "audio/mpeg",
        manifest.readingOrder[0].link.type?.fullType
      )
      assertEquals(
        "http://archive.org/download/gleams_of_sunshine_1607_librivox/gleamsofsunshine_01_chant.mp3",
        manifest.readingOrder[0].link.hrefURI.toString()
      )

      val encrypted0 = manifest.readingOrder[0].link.properties.encrypted!!
      assertEquals(
        "http://www.feedbooks.com/audiobooks/access-restriction",
        encrypted0.scheme
      )
      assertEquals(
        "https://www.cantookaudio.com",
        (encrypted0.values["profile"] as PlayerManifestScalar.PlayerManifestScalarString).text
      )
    }

    assertEquals(3, manifest.links.size)
    assertEquals(
      "cover",
      manifest.links[0].relation[0]
    )
    assertEquals(
      180,
      manifest.links[0].width
    )
    assertEquals(
      180,
      manifest.links[0].height
    )
    assertEquals(
      "image/jpeg",
      manifest.links[0].type?.fullType
    )
    assertEquals(
      "http://archive.org/services/img/gleams_of_sunshine_1607_librivox",
      manifest.links[0].hrefURI.toString()
    )

    assertEquals(
      "self",
      manifest.links[1].relation[0]
    )
    assertEquals(
      "application/audiobook+json",
      manifest.links[1].type?.fullType
    )
    assertEquals(
      "https://api.archivelab.org/books/gleams_of_sunshine_1607_librivox/opds_audio_manifest",
      manifest.links[1].hrefURI.toString()
    )

    assertEquals(
      "license",
      manifest.links[2].relation[0]
    )
    assertEquals(
      "application/vnd.readium.license.status.v1.0+json",
      manifest.links[2].type?.fullType
    )
    assertEquals(
      "http://example.com/license/status",
      manifest.links[2].hrefURI.toString()
    )

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  @Test
  fun testOkFindaway0() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("findaway"),
        input = this.resource("findaway.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result

    assertEquals(
      "Most Dangerous",
      manifest.metadata.title
    )
    assertEquals(
      "urn:librarysimplified.org/terms/id/Bibliotheca%20ID/hxaee89",
      manifest.metadata.identifier
    )

    val encrypted = manifest.metadata.encrypted!!
    assertEquals(
      "http://librarysimplified.org/terms/drm/scheme/FAE",
      encrypted.scheme
    )

    assertEquals(
      "REDACTED0",
      encrypted.values["findaway:accountId"].toString()
    )
    assertEquals(
      "REDACTED1",
      encrypted.values["findaway:checkoutId"].toString()
    )
    assertEquals(
      "REDACTED2",
      encrypted.values["findaway:sessionKey"].toString()
    )
    assertEquals(
      "REDACTED3",
      encrypted.values["findaway:fulfillmentId"].toString()
    )
    assertEquals(
      "REDACTED4",
      encrypted.values["findaway:licenseId"].toString()
    )

    assertEquals(
      "1",
      manifest.readingOrder[0].link.properties.extras["findaway:sequence"].toString()
    )
    assertEquals(
      "0",
      manifest.readingOrder[0].link.properties.extras["findaway:part"].toString()
    )

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  @Test
  fun testOkFindaway20201015() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("findaway"),
        input = this.resource("findaway-20201015.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result

    assertEquals(
      "Man Riding West",
      manifest.metadata.title
    )
    assertEquals(
      "urn:librarysimplified.org/terms/id/Bibliotheca%20ID/ebwowg9",
      manifest.metadata.identifier
    )

    val encrypted = manifest.metadata.encrypted!!
    assertEquals(
      "http://librarysimplified.org/terms/drm/scheme/FAE",
      encrypted.scheme
    )

    assertEquals(
      "REDACTED0",
      encrypted.values["findaway:accountId"].toString()
    )
    assertEquals(
      "REDACTED1",
      encrypted.values["findaway:checkoutId"].toString()
    )
    assertEquals(
      "REDACTED2",
      encrypted.values["findaway:sessionKey"].toString()
    )
    assertEquals(
      "REDACTED3",
      encrypted.values["findaway:fulfillmentId"].toString()
    )
    assertEquals(
      "REDACTED4",
      encrypted.values["findaway:licenseId"].toString()
    )

    assertEquals(
      "1",
      manifest.readingOrder[0].link.properties.extras["findaway:sequence"].toString()
    )
    assertEquals(
      "0",
      manifest.readingOrder[0].link.properties.extras["findaway:part"].toString()
    )

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  @Test
  fun testOkFindawayLeading0() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("findaway"),
        input = this.resource("findaway_leading_0.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result

    val encrypted = manifest.metadata.encrypted!!

    assertEquals(
      "012345",
      encrypted.values["findaway:fulfillmentId"].toString()
    )

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  @Test
  fun testOkFeedbooks1() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("feedbooks"),
        input = this.resource("feedbooks_1.json"),
        extensions = listOf()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkFeedbooks1Values(manifest)

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  @Test
  fun testOkFeedbooks1WithExtensions() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("feedbooks"),
        input = this.resource("feedbooks_1.json"),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    this.checkFeedbooks1Values(manifest)

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  @Test
  fun testOkIGen() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("igen"),
        input = this.resource("igen.json"),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    assertEquals("urn:isbn:9781508245063", manifest.metadata.identifier)

    for (item in manifest.readingOrder) {
      assertFalse(item.link.hrefURI.toString().startsWith("/"))
    }

    val tocItems = PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }
    this.checkTOCInvariants(tocItems, manifest)
  }

  private fun checkFeedbooks1Values(manifest: PlayerManifest) {
    assertEquals(
      "urn:uuid:35c5e499-9cb9-46e0-9e47-c517973f9e7f",
      manifest.metadata.identifier
    )
    assertEquals(
      "Rise of the Dragons, Book 1",
      manifest.metadata.title
    )

    /*
     * I don't think we really need to check the contents of all 41 spine items.
     * The rest of the test suite should hopefully cover this sufficiently.
     */

    assertEquals(41, manifest.readingOrder.size)
    assertEquals(3, manifest.links.size)
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
        uri = URI.create("demon"),
        input = this.resource("audible/demon.json"),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest =
      success.result
    val tocItems =
      PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }

    this.checkTOCInvariants(tocItems, manifest)
  }

  private fun checkTOCInvariants(
    tocItems: PlayerManifestTOC,
    manifest: PlayerManifest
  ) {
    println("# Reading Order Items")
    manifest.readingOrder.forEachIndexed { index, link ->
      println("[$index] ${tocItems.readingOrderIntervals[link.id]}")
    }

    println("# TOC Items")
    var tocTotal = 0L
    tocItems.tocItemsInOrder.forEachIndexed { index, toc ->
      val size = toc.intervalAbsoluteMilliseconds.size()
      tocTotal += size.value
      println("[$index] Size $size | Total $tocTotal | Item $toc")
    }

    val durationSum =
      manifest.readingOrder.sumOf { item ->
        Duration.standardSeconds((item.link.duration ?: 0L).toLong()).millis
      }

    val readingOrderIntervalsSum =
      tocItems.readingOrderIntervals.values.sumOf {
        i -> (i.upper - i.lower).value + 1
      }
    val tocItemIntervalsSum =
      tocItems.tocItemsInOrder.sumOf {
          i -> (i.intervalAbsoluteMilliseconds.upper - i.intervalAbsoluteMilliseconds.lower).value + 1
      }

    assertEquals(
      durationSum,
      readingOrderIntervalsSum,
      "Sum of reading order intervals must equal reading order duration sum"
    )
    assertEquals(
      durationSum,
      tocItemIntervalsSum,
      "Sum of TOC item intervals must equal reading order duration sum"
    )

    for (item in manifest.readingOrder) {
      val duration = (item.link.duration ?: 1).toLong() + 10
      for (time in 0..duration) {
        val tocItem = tocItems.lookupTOCItem(item.id, PlayerMillisecondsReadingOrderItem(time))
        assertNotNull(tocItem, "TOC item for ${item.id} time $time must not be null")
      }
    }
  }

  /**
   * "This one is a good example of a tricky manifest where some chapters span multiple tracks.
   * For example the chapter “London Bridge“ spans 3 different tracks. And “The City of London“
   * chapter has two almost complete tracks as part of it.
   *
   * It also has an example of a very small chapter “Book One - Quicksilver“
   *
   * This is an example of a manifest where figuring out the length of the final chapter is
   * slightly more difficult because there is no following chapter, so you have to use the duration
   * of the tracks to the end of the book to calculate it."
   */

  @Test
  fun testDifficultManifest1() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("quicksilver"),
        input = this.resource("audible/quicksilver.json"),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest =
      success.result
    val tocItems =
      PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }

    this.checkTOCInvariants(tocItems, manifest)
  }

  /**
   * "This one has an example of a 0 length chapter “Journeyman“, which is another weird edge case.
   * In a ticket we asked DeMarque about fixing these, but it might be good to be able to handle
   * this case."
   */

  @Test
  fun testDifficultManifest2() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("weapon"),
        input = this.resource("audible/weapon.json"),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest =
      success.result
    val tocItems =
      PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }

    this.checkTOCInvariants(tocItems, manifest)
  }

  /**
   * "Another example of 0 length chapters:"
   */

  @Test
  fun testDifficultManifest3() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("yellow_eyes"),
        input = this.resource("audible/yellow_eyes.json"),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest =
      success.result
    val tocItems =
      PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }

    this.checkTOCInvariants(tocItems, manifest)
  }

  /**
   * Random game audio.
   */

  @Test
  fun testRandomGameAudio() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("random-game-audio.json"),
        input = this.resource("random-game-audio.json"),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest =
      success.result
    val tocItems =
      PlayerManifestTOCs.createTOC(manifest) { index -> "Track $index" }

    assertEquals(4, tocItems.tocItemsInOrder.size)
    assertEquals(0, tocItems.tocItemsInOrder[0].intervalAbsoluteMilliseconds.lower.value)
    assertEquals(10000, tocItems.tocItemsInOrder[1].intervalAbsoluteMilliseconds.lower.value)
    assertEquals(20000, tocItems.tocItemsInOrder[2].intervalAbsoluteMilliseconds.lower.value)
    assertEquals(30000, tocItems.tocItemsInOrder[3].intervalAbsoluteMilliseconds.lower.value)

    this.checkTOCInvariants(tocItems, manifest)
  }

  /**
   * I-Strahd
   */

  @Test
  fun testIStrahd() {
    val result =
      ManifestParsers.parse(
        uri = URI.create("i_strahd.json"),
        input = this.resource("audible/i_strahd.json"),
        extensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
      )
    this.log().debug("result: {}", result)
    assertTrue(result is ParseResult.Success, "Result is success")

    val success: ParseResult.Success<PlayerManifest> =
      result as ParseResult.Success<PlayerManifest>

    val manifest = success.result
    assertTrue(ExoLCP.isLCP(manifest), "Manifest is inferred as LCP")
  }
}
