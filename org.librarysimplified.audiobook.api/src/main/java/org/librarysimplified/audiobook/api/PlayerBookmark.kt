package org.librarysimplified.audiobook.api

import org.joda.time.DateTime
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID

class PlayerBookmark(
  val date: DateTime,
  val readingOrderID: PlayerManifestReadingOrderID,
  val offsetMilliseconds: Long
) {
  val position: PlayerPosition =
    PlayerPosition(this.readingOrderID, this.offsetMilliseconds)
}
