package org.librarysimplified.audiobook.api

import java.io.File

/**
 * The source of a book.
 */

sealed class PlayerBookSource {

  /**
   * The book source is a packaged audiobook file.
   */

  @Deprecated(message = "This is a compatibility option to ease migration for old apps.")
  data class PlayerBookSourcePackagedBook(
    val file: File
  ) : PlayerBookSource()

  /**
   * The book data is taken from a license file. This is the usual situation for streamed LCP
   * audiobooks, where the underlying player is responsible for streaming encrypted audiobook
   * chapters from a remote zip file.
   */

  data class PlayerBookSourceLicenseFile(
    val file: File
  ) : PlayerBookSource()

  /**
   * The book data is accessed from the manifest alone.
   */

  data object PlayerBookSourceManifestOnly : PlayerBookSource()
}
