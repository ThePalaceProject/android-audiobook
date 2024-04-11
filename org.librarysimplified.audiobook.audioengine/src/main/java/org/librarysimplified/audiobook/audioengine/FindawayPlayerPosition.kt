package org.librarysimplified.audiobook.audioengine

import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCItem

data class FindawayPlayerPosition(
  val readingOrderItem: PlayerReadingOrderItemType,
  val offsetMilliseconds: Long,
  val part: Int,
  val chapter: Int,
  val tocItem: PlayerManifestTOCItem,
  val totalBookDurationRemaining: Duration
)
