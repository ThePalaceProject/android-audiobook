package org.librarysimplified.audiobook.audioengine

import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCItem
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem

data class FindawayPlayerPosition(
  val readingOrderItem: PlayerReadingOrderItemType,
  val readingOrderItemOffsetMilliseconds: PlayerMillisecondsReadingOrderItem,
  val part: Int,
  val chapter: Int,
  val tocItem: PlayerManifestTOCItem,
  val totalBookDurationRemaining: Duration
)
