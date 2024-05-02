package org.librarysimplified.audiobook.manifest.api

import java.net.URI

/**
 * A reading order identifier.
 *
 * @see "https://github.com/ThePalaceProject/mobile-specs/tree/main/audiobook-reading-order-ids"
 */

data class PlayerManifestReadingOrderID(
  val text: String
) {

  companion object {

    /**
     * Create a reading order item identifier from the possibly null URI.
     *
     * @param index The reading order item index
     * @param uri The URI
     * @return An identifier
     */

    fun create(
      index: Int,
      uri: URI?
    ): PlayerManifestReadingOrderID {
      return if (uri != null) {
        PlayerManifestReadingOrderID(uri.toString())
      } else {
        PlayerManifestReadingOrderID(
          "urn:org.thepalaceproject:readingOrderItem:$index"
        )
      }
    }
  }

  override fun toString(): String {
    return this.text
  }
}
