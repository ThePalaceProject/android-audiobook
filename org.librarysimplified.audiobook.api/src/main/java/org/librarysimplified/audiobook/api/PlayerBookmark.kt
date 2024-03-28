package org.librarysimplified.audiobook.api

import org.joda.time.DateTime
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID

/**
 * A bookmark.
 */

class PlayerBookmark(
  val kind: PlayerBookmarkKind,
  val title: String,
  val date: DateTime,
  val readingOrderID: PlayerManifestReadingOrderID,
  val offsetMilliseconds: Long
) {
  val position: PlayerPosition =
    PlayerPosition(this.readingOrderID, this.offsetMilliseconds)
}
