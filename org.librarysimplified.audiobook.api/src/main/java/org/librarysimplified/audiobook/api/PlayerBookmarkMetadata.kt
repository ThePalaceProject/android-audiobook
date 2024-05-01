package org.librarysimplified.audiobook.api

import org.joda.time.DateTime
import org.joda.time.Duration

/**
 * Informative, non-critical information included with a bookmark for display purposes.
 */

data class PlayerBookmarkMetadata(

  /**
   * The bookmark creation time.
   */

  val creationTime: DateTime,

  /**
   * The table of contents item within which the current position falls
   */

  val chapterTitle: String,

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
) {

  companion object {

    /**
     * Convert the given position metadata and creation time to bookmark metadata.
     */

    fun fromPositionMetadata(
      positionMetadata: PlayerPositionMetadata,
      creationTime: DateTime,
    ): PlayerBookmarkMetadata {
      return PlayerBookmarkMetadata(
        creationTime = creationTime,
        chapterTitle = positionMetadata.tocItem.title,
        totalRemainingBookTime = positionMetadata.totalRemainingBookTime,
        chapterProgressEstimate = positionMetadata.chapterProgressEstimate,
        bookProgressEstimate = positionMetadata.bookProgressEstimate
      )
    }

    /**
     * Convert the given position metadata to bookmark metadata.
     */

    fun fromPositionMetadata(
      positionMetadata: PlayerPositionMetadata
    ): PlayerBookmarkMetadata {
      return this.fromPositionMetadata(positionMetadata, DateTime.now())
    }
  }
}
