package org.librarysimplified.audiobook.api

import java.io.File

/**
 * The source of a book.
 */

sealed class PlayerBookSource {

  /**
   * The book data is taken from a file. This is the usual situation for a "packaged" audiobook,
   * where book chapters are packaged into a zip file.
   */

  data class PlayerBookSourceFile(
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
}
