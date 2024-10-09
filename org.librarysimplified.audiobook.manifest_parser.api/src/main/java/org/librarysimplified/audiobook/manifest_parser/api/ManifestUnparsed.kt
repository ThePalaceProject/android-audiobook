package org.librarysimplified.audiobook.manifest_parser.api

import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID

/**
 * A not-yet-parsed manifest.
 */

data class ManifestUnparsed(
  val palaceId: PlayerPalaceID,
  val data: ByteArray
)
