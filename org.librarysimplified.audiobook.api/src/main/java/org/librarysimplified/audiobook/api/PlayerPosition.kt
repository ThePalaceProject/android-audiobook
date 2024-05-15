package org.librarysimplified.audiobook.api

import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem

/**
 * The playback position of the player.
 */

data class PlayerPosition(
  val readingOrderID: PlayerManifestReadingOrderID,
  val offsetMilliseconds: PlayerMillisecondsReadingOrderItem
)
