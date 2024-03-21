package org.librarysimplified.audiobook.manifest.api

/**
 * The subset of links that are allowed to appear in reading orders: We mandate that reading
 * order items must stable identifiers and, if a link with a href is present, it must be a
 * non-templated link.
 */

data class PlayerManifestReadingOrderItem(
  val id: PlayerManifestReadingOrderID,
  val link: PlayerManifestLink.LinkBasic
)
