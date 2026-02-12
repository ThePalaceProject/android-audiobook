package org.librarysimplified.audiobook.api

import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import java.io.File

/**
 * A request to download data from a given link.
 */

data class PlayerDownloadRequest(
  val link: PlayerManifestLink,
  val userAgent: PlayerUserAgent,
  val outputFile: File,
  val outputFileTemp: File,
  val onProgress: (Int) -> Unit,
  val onCompletion: (File) -> Unit,
  val kind: Kind,
  val authorizationHandler: PlayerAuthorizationHandlerType
) {
  enum class Kind {
    MANIFEST,
    CHAPTER,
    WHOLE_BOOK
  }
}
