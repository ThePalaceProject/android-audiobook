package org.librarysimplified.audiobook.manifest.api

import one.irradia.mime.api.MIMEType
import java.net.URI

data class PlayerManifestTOCItem(
  val title: String,
  val part: Int,
  val chapter: Int,
  val type: MIMEType,
  val duration: Double?,
  val offset: Double,
  val uri: URI,
  val originalLink: PlayerManifestLink
) : Comparable<PlayerManifestTOCItem> {

  override fun compareTo(other: PlayerManifestTOCItem): Int {
    return Comparator.comparing(PlayerManifestTOCItem::part)
      .thenComparing(PlayerManifestTOCItem::chapter)
      .compare(this, other)
  }
}
