package org.librarysimplified.audiobook.open_access

import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderItem

/**
 * A spine item transformed to expose the information critical to the ExoPlayer engine.
 */

data class ExoManifestMutableReadingOrderItem(
  val index: Int,
  var item: PlayerManifestReadingOrderItem
)
