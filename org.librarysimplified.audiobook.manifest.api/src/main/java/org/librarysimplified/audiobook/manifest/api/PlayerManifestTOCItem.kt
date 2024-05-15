package org.librarysimplified.audiobook.manifest.api

import org.joda.time.Duration

/**
 * A TOC item in a manifest.
 */

data class PlayerManifestTOCItem(
  val title: String,
  val index: Int,
  val chapter: Int,

  /**
   * The interval covered by this TOC item on the absolute timeline.
   */

  val intervalAbsoluteMilliseconds: PlayerMillisecondsAbsoluteInterval
) : Comparable<PlayerManifestTOCItem> {

  override fun compareTo(other: PlayerManifestTOCItem): Int {
    return Comparator.comparing(PlayerManifestTOCItem::index)
      .compare(this, other)
  }

  val duration: Duration
    get() = Duration.millis(this.durationMilliseconds)

  val durationMilliseconds: Long
    get() {
      val absUpper = this.intervalAbsoluteMilliseconds.upper.value
      val absLower = this.intervalAbsoluteMilliseconds.lower.value
      return (absUpper - absLower) + 1
    }
}
