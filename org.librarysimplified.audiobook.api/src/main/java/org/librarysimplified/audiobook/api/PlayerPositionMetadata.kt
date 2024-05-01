package org.librarysimplified.audiobook.api

import org.joda.time.Duration
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCItem

data class PlayerPositionMetadata(

  /**
   * The table of contents item within which the current position falls
   */

  val tocItem: PlayerManifestTOCItem,

  /**
   * The total remaining time in the entire book.
   */

  val totalRemainingBookTime: Duration,

  /**
   * The current progress estimate within the current chapter, in the range [0.0, 1.0]
   */

  val chapterProgressEstimate: Double,

  /**
   * The current progress estimate within the current book, in the range [0.0, 1.0]
   */

  val bookProgressEstimate: Double
)
