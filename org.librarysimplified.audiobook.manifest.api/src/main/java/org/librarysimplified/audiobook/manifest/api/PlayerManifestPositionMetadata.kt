package org.librarysimplified.audiobook.manifest.api

import org.joda.time.Duration

data class PlayerManifestPositionMetadata(

  /**
   * The table of contents item within which the current position falls
   */

  val tocItem: PlayerManifestTOCItem,

  /**
   * The current time relative to the current TOC item
   */

  val tocItemPosition: Duration,

  /**
   * The current time remaining in the current TOC item
   */

  val tocItemRemaining: Duration,

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
