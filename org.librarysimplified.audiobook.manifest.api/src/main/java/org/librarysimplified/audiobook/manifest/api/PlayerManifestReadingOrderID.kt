package org.librarysimplified.audiobook.manifest.api

import java.net.URI

data class PlayerManifestReadingOrderID(
  val text: String
) {

  companion object {
    fun create(
      index: Int,
      uri: URI?
    ): PlayerManifestReadingOrderID {
      return if (uri != null) {
        PlayerManifestReadingOrderID(uri.toString())
      } else {
        PlayerManifestReadingOrderID(
          "urn:org.thepalaceproject:reading_order_item:$index"
        )
      }
    }
  }
}
