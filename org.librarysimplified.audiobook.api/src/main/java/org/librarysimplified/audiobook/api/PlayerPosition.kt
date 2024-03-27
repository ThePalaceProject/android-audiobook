package org.librarysimplified.audiobook.api

import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID

/**
 * The playback position of the player.
 */

data class PlayerPosition(
  val readingOrderID: PlayerManifestReadingOrderID,
  val offsetMilliseconds: Long
)
