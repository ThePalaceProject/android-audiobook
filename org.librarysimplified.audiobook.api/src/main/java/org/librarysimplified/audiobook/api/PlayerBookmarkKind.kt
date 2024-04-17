package org.librarysimplified.audiobook.api

/**
 * The kind of a bookmark.
 */

enum class PlayerBookmarkKind {

  /**
   * The bookmark was explicitly created.
   */

  EXPLICIT,

  /**
   * The bookmark was automatically generated to track the last read time.
   */

  LAST_READ
}
