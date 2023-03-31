package org.librarysimplified.audiobook.api

/**
 * The playback position of the player.
 *
 * The 'startOffset' field corresponds to the offset value where the chapter's track starts to be
 * played at. For instance, if a chapter's hrefURI ends at "t=70", it means the startOffset will be
 * 70.
 *
 * The 'currentOffset' field corresponds to the playing position of the chapter and it's updated
 * every time there's a change on the chapter's elapsed time (when a second goes by, when the user
 * moves back/forward, etc.)
 */

data class PlayerPosition(
  val title: String?,
  val part: Int,
  val chapter: Int,
  val startOffset: Long,
  val currentOffset: Long
)
