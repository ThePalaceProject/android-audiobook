package org.librarysimplified.audiobook.media3

import java.net.URI

/**
 * The degree of download support available for the current book.
 */

sealed class ExoDownloadSupport {

  /**
   * The entire book can be downloaded a single file from the given URI.
   */

  data class DownloadEntireBookAsFile(
    val targetURI: URI,
    val licenseBytes: ByteArray
  ) : ExoDownloadSupport()

  /**
   * Individual chapters from within the manifest can be downloaded.
   */

  data object DownloadIndividualChaptersAsFiles : ExoDownloadSupport()

  /**
   * Downloading isn't supported at all.
   */

  data object DownloadUnsupported : ExoDownloadSupport()
}
