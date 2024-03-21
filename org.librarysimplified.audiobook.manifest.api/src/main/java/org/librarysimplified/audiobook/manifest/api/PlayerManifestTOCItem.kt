package org.librarysimplified.audiobook.manifest.api

import com.io7m.kabstand.core.IntervalL

data class PlayerManifestTOCItem(
  val title: String,
  val part: Int,
  val chapter: Int,
  val intervalAbsoluteSeconds: IntervalL,
  val readingOrderLink: PlayerManifestReadingOrderItem
) : Comparable<PlayerManifestTOCItem> {

  override fun compareTo(other: PlayerManifestTOCItem): Int {
    return Comparator.comparing(PlayerManifestTOCItem::part)
      .thenComparing(PlayerManifestTOCItem::chapter)
      .compare(this, other)
  }
}
