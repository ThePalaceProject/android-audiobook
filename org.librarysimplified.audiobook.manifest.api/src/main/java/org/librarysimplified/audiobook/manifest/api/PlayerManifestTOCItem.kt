package org.librarysimplified.audiobook.manifest.api

import com.io7m.kabstand.core.IntervalL
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

  val intervalAbsoluteMilliseconds: IntervalL,

  /**
   * The reading order item to which this TOC item belongs.
   */

  val readingOrderLink: PlayerManifestReadingOrderItem,

  /**
   * The millisecond offset from the start of the reading order item for this TOC item.
   */

  val readingOrderOffsetMilliseconds: Long,
) : Comparable<PlayerManifestTOCItem> {

  override fun compareTo(other: PlayerManifestTOCItem): Int {
    return Comparator.comparing(PlayerManifestTOCItem::index)
      .compare(this, other)
  }

  val duration: Duration
    get() = Duration.millis(this.durationMilliseconds)

  val durationMilliseconds: Long
    get() = (this.intervalAbsoluteMilliseconds.upper - this.intervalAbsoluteMilliseconds.lower) + 1
}
