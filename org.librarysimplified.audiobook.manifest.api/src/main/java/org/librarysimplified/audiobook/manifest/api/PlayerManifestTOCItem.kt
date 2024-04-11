package org.librarysimplified.audiobook.manifest.api

import com.io7m.kabstand.core.IntervalL
import org.joda.time.Duration

data class PlayerManifestTOCItem(
  val title: String,
  val index: Int,
  val chapter: Int,
  val intervalAbsoluteMilliseconds: IntervalL,
  val readingOrderLink: PlayerManifestReadingOrderItem,
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
