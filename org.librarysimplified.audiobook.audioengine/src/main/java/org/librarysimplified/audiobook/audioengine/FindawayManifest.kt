package org.librarysimplified.audiobook.audioengine

import android.content.Context
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderItem
import org.librarysimplified.audiobook.manifest.api.PlayerManifestScalar
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOC
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCs
import org.librarysimplified.audiobook.manifest.api.R
import org.slf4j.LoggerFactory

/**
 * A manifest transformed such that it contains information relevant to the Findaway audio engine.
 */

data class FindawayManifest(
  val title: String,
  val id: PlayerBookID,
  val accountId: String,
  val checkoutId: String,
  val sessionKey: String,
  val fulfillmentId: String,
  val licenseId: String,
  val readingOrderItems: List<FindawayManifestMutableReadingOrderItem>,
  val toc: PlayerManifestTOC,
  val manifest: PlayerManifest
) {

  companion object {

    private val logger = LoggerFactory.getLogger(FindawayManifest::class.java)

    private fun valueString(map: Map<String, PlayerManifestScalar>, key: String): String {
      return (map[key] ?: throw IllegalArgumentException(
        StringBuilder(128)
          .append("Missing required key.\n")
          .append("  Key: ")
          .append(key)
          .append('\n')
          .toString()
      )).toString()
    }

    private fun valueInt(
      map: Map<String, PlayerManifestScalar>,
      key: String
    ): Int {
      try {
        return (map[key] ?: throw IllegalArgumentException(
          StringBuilder(128)
            .append("Missing required key.\n")
            .append("  Key: ")
            .append(key)
            .append('\n')
            .toString()
        )).toString().toInt()
      } catch (e: NumberFormatException) {
        throw IllegalArgumentException(
          StringBuilder(128)
            .append("Malformed Int value.\n")
            .append("  Key: ")
            .append(key)
            .append('\n')
            .toString(), e
        )
      }
    }

    fun transform(
      context: Context,
      bookID: PlayerBookID,
      manifest: PlayerManifest
    ): PlayerResult<FindawayManifest, Exception> {
      try {
        val encrypted = manifest.metadata.encrypted
          ?: throw IllegalArgumentException("Manifest is missing the required encrypted section")

        if (encrypted.scheme != "http://librarysimplified.org/terms/drm/scheme/FAE") {
          throw IllegalArgumentException(
            StringBuilder(128)
              .append("Incorrect scheme.\n")
              .append("  Expected: ")
              .append("http://librarysimplified.org/terms/drm/scheme/FAE")
              .append('\n')
              .append("  Received: ")
              .append(encrypted.scheme)
              .append('\n')
              .toString()
          )
        }

        val manifestRewritten =
          PlayerManifest(
            palaceId = manifest.palaceId,
            originalBytes = manifest.originalBytes,
            readingOrder = transformReadingOrder(manifest.readingOrder),
            metadata = manifest.metadata,
            links = manifest.links,
            extensions = manifest.extensions,
            toc = null
          )

        val readingOrderItems =
          manifestRewritten.readingOrder.mapIndexed { _, item ->
            val part =
              valueInt(item.link.properties.extras, "findaway:part")
            val sequence =
              valueInt(item.link.properties.extras, "findaway:sequence")

            FindawayManifestMutableReadingOrderItem(
              id = item.id,
              part = part,
              sequence = sequence,
              duration = item.link.duration ?: 0.0
            )
          }

        val tableOfContents =
          makeTOC(manifestRewritten, context)

        return PlayerResult.Success(
          result =
          FindawayManifest(
            accountId = this.valueString(encrypted.values, "findaway:accountId"),
            checkoutId = this.valueString(encrypted.values, "findaway:checkoutId"),
            fulfillmentId = this.valueString(encrypted.values, "findaway:fulfillmentId"),
            id = bookID,
            licenseId = this.valueString(encrypted.values, "findaway:licenseId"),
            readingOrderItems = readingOrderItems,
            sessionKey = this.valueString(encrypted.values, "findaway:sessionKey"),
            title = manifestRewritten.metadata.title,
            toc = tableOfContents,
            manifest = manifestRewritten
          )
        )
      } catch (e: Exception) {
        this.logger.error("Parse error: ", e)
        return PlayerResult.Failure(e)
      }
    }

    /**
     * Rewrite links to conform to the `audiobook-reading-order-ids` spec.
     *
     * @see "https://github.com/ThePalaceProject/mobile-specs/tree/main/audiobook-reading-order-ids"
     */

    private fun transformReadingOrder(
      readingOrder: List<PlayerManifestReadingOrderItem>
    ): List<PlayerManifestReadingOrderItem> {
      return readingOrder.map { item ->
        val part =
          valueInt(item.link.properties.extras, "findaway:part")
        val sequence =
          valueInt(item.link.properties.extras, "findaway:sequence")
        val id =
          PlayerManifestReadingOrderID(
            "urn:org.thepalaceproject:findaway:$part:$sequence"
          )
        PlayerManifestReadingOrderItem(id = id, link = item.link)
      }
    }

    private fun makeTOC(
      manifest: PlayerManifest,
      context: Context
    ): PlayerManifestTOC {
      return PlayerManifestTOCs.createTOC(
        manifest,
        defaultTrackTitle = { index ->
          context.getString(R.string.player_manifest_audiobook_default_track_n, index + 1)
        }
      )
    }
  }
}
