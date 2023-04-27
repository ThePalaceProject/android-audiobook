package org.librarysimplified.audiobook.api

import org.joda.time.DateTime
import java.net.URI

class PlayerBookmark(
  val date: DateTime,
  val position: PlayerPosition,
  val duration: Long,
  val uri: URI?
)
