package org.librarysimplified.audiobook.api

import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPClientType
import java.io.File

/**
 * A request to download data from a given link.
 */

data class PlayerDownloadRequest(
  val link: PlayerManifestLink,
  val httpClient: LSHTTPClientType,
  val outputFile: File,
  val outputFileTemp: File,
  val onProgress: (Int) -> Unit,
  val onCompletion: (File) -> Unit,
  val kind: Kind,
  val authorizationHandler: PlayerAuthorizationHandlerType
) {
  /**
   * The kind of object being downloaded.
   */

  enum class Kind {

    /** A manifest file. */

    MANIFEST,

    /** A chapter within a manifest (typically an audio file) */

    CHAPTER,

    /** An entire book (typically an LCP publication) */

    WHOLE_BOOK,

    /** A license file (typically an LCP license) */

    LICENSE
  }
}
