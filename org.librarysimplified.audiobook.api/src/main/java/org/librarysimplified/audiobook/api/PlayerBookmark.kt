package org.librarysimplified.audiobook.api

import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID

/**
 * A bookmark.
 */

data class PlayerBookmark(
  val kind: PlayerBookmarkKind,
  val readingOrderID: PlayerManifestReadingOrderID,
  val offsetMilliseconds: Long,
  val metadata: PlayerBookmarkMetadata
) {
  val position: PlayerPosition =
    PlayerPosition(this.readingOrderID, this.offsetMilliseconds)
}
