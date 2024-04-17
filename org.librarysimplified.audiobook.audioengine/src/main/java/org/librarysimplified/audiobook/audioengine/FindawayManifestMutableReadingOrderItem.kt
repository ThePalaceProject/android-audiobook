package org.librarysimplified.audiobook.audioengine

import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID

/**
 * A spine item transformed to expose the information critical to the Findaway engine.
 */

data class FindawayManifestMutableReadingOrderItem(
  val id: PlayerManifestReadingOrderID,
  val part: Int,
  val sequence: Int,
  val duration: Double
)
