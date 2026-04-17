package org.librarysimplified.audiobook.views

import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerDownloadProgress
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerPauseReason
import org.librarysimplified.audiobook.api.PlayerPlaybackIntention
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPlaybackStatus.BUFFERING
import org.librarysimplified.audiobook.api.PlayerPlaybackStatus.PAUSED
import org.librarysimplified.audiobook.api.PlayerPlaybackStatus.PLAYING
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOC
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsAbsolute
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * A reference to the current player and book. Every operation is protected by a single
 * coarse lock. The intention is that it should be physically impossible to observe a closed
 * `Player` reference outside of this class.
 */

internal object PlayerReference {

  private val logger =
    LoggerFactory.getLogger(PlayerReference::class.java)

  private val playerAndBookLock = Any()
  private var playerAndBookRef: PlayerBookAndPlayer? = null

  fun <T> withPlayer(op: (PlayerBookAndPlayer?) -> T): T {
    return synchronized(this.playerAndBookLock) {
      op.invoke(this.playerAndBookRef)
    }
  }

  fun withPlayerIfPresent(op: (PlayerBookAndPlayer) -> Unit) {
    return synchronized(this.playerAndBookLock) {
      val r = this.playerAndBookRef
      if (r != null) {
        op.invoke(r)
      }
    }
  }

  fun <T> withPlayerIfPresentElse(op: (PlayerBookAndPlayer) -> T, defaultValue: T): T {
    return synchronized(this.playerAndBookLock) {
      val r = this.playerAndBookRef
      if (r != null) {
        op.invoke(r)
      } else {
        defaultValue
      }
    }
  }

  fun opPlaybackRate(): PlayerPlaybackRate {
    return this.withPlayer { r ->
      try {
        if (r == null) {
          return@withPlayer PlayerPlaybackRate.NORMAL_TIME
        }
        val player = r.player
        player.playbackRate
      } catch (_: Throwable) {
        PlayerPlaybackRate.NORMAL_TIME
      }
    }
  }

  fun opPlayerClose() {
    this.withPlayer { r ->
      try {
        if (r != null) {
          this.logger.debug("Closing old player instance {}", r.player)
          r.player.close()
        } else {
          this.logger.debug("No existing player instance is present")
        }
      } catch (e: Throwable) {
        this.logger.debug("opPlayerClose: ", e)
      } finally {
        this.playerAndBookRef = null
        if (r != null) {
          this.logger.debug("Player instance {} is now inaccessible", r.player)
        }
      }
    }
  }

  fun opNewPlayer(
    newPair: PlayerBookAndPlayer
  ) {
    this.withPlayer {
      check(!newPair.player.isClosed) {
        "New player instance ${newPair.player} must not be closed!"
      }
      this.opPlayerClose()
      this.playerAndBookRef = newPair
    }
  }

  fun opPause(reason: PlayerPauseReason) {
    this.withPlayerIfPresent { r ->
      r.player.pause(reason)
    }
  }

  fun opPlay() {
    this.withPlayerIfPresent { r ->
      r.player.play()
    }
  }

  fun opIsStreamingSupported(): Boolean {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          r.audioBook.supportsStreaming
        } catch (e: Throwable) {
          this.logger.debug("opIsStreamingSupported: ", e)
          false
        }
      },
      defaultValue = false
    )
  }

  fun opSkipPlayhead(seekIncrement: Long) {
    this.withPlayerIfPresent { r ->
      try {
        r.player.skipPlayhead(seekIncrement)
      } catch (e: Throwable) {
        this.logger.debug("opSkipPlayhead: ", e)
      }
    }
  }

  fun opPlaybackRateSet(rate: PlayerPlaybackRate) {
    this.withPlayerIfPresent { r ->
      try {
        r.player.playbackRate = rate
      } catch (e: Throwable) {
        this.logger.debug("opPlaybackRateSet: ", e)
      }
    }
  }

  fun opIsPlaying(): Boolean {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          when (r.player.playbackStatus) {
            BUFFERING -> true
            PLAYING -> true
            PAUSED -> false
          }
        } catch (e: Throwable) {
          this.logger.debug("opIsPlaying: ", e)
          false
        }
      },
      defaultValue = false
    )
  }

  fun opIsBuffering(): Boolean {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          when (r.player.playbackStatus) {
            BUFFERING -> true
            PLAYING -> false
            PAUSED -> false
          }
        } catch (e: Throwable) {
          this.logger.debug("opIsBuffering: ", e)
          false
        }
      },
      defaultValue = false
    )
  }

  fun opMovePlayheadTo(position: PlayerPosition) {
    return this.withPlayerIfPresent { r ->
      try {
        r.player.movePlayheadToLocation(position)
      } catch (e: Throwable) {
        this.logger.debug("opMovePlayheadTo: ", e)
      }
    }
  }

  fun opMovePlayheadToAbsoluteTime(newOffset: PlayerMillisecondsAbsolute) {
    return this.withPlayerIfPresent { r ->
      try {
        r.player.movePlayheadToAbsoluteTime(newOffset)
      } catch (e: Throwable) {
        this.logger.debug("opMovePlayheadToAbsoluteTime: ", e)
      }
    }
  }

  fun opBookmarkCreate() {
    return this.withPlayerIfPresent { r ->
      try {
        r.player.bookmark()
      } catch (e: Throwable) {
        this.logger.debug("opBookmarkCreate: ", e)
      }
    }
  }

  fun opBookmarkDelete(bookmark: PlayerBookmark) {
    return this.withPlayerIfPresent { r ->
      try {
        r.player.bookmarkDelete(bookmark)
      } catch (e: Throwable) {
        this.logger.debug("opBookmarkDelete: ", e)
      }
    }
  }

  fun opManifest(): PlayerManifest? {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          r.audioBook.manifest
        } catch (e: Throwable) {
          this.logger.debug("opManifest: ", e)
          null
        }
      },
      defaultValue = null
    )
  }

  fun opPlayOrPauseAsAppropriate(reason: PlayerPauseReason) {
    this.withPlayerIfPresent { r ->
      try {
        when (r.player.playbackIntention) {
          PlayerPlaybackIntention.SHOULD_BE_PLAYING -> r.player.pause(reason)
          PlayerPlaybackIntention.SHOULD_BE_STOPPED -> r.player.play()
        }
      } catch (e: Throwable) {
        this.logger.debug("opPlayOrPauseAsAppropriate: ", e)
      }
    }
  }

  fun opIsDownloading(): Boolean {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          val book = r.audioBook
          book.downloadTasks.any { t -> t.status is PlayerDownloadTaskStatus.Downloading }
        } catch (e: Throwable) {
          this.logger.debug("opIsDownloading: ", e)
          false
        }
      },
      defaultValue = false
    )
  }

  fun opIsDownloadingCompleted(): Boolean {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          val book = r.audioBook
          book.downloadTasks.all { t -> t.status is PlayerDownloadTaskStatus.IdleDownloaded }
        } catch (e: Throwable) {
          this.logger.debug("opIsDownloadingCompleted: ", e)
          false
        }
      },
      defaultValue = false
    )
  }

  fun opIsDownloadingAnyFailed(): Boolean {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          val book = r.audioBook
          book.downloadTasks.any { t -> t.status is PlayerDownloadTaskStatus.Failed }
        } catch (e: Throwable) {
          this.logger.debug("opIsDownloadingAnyFailed: ", e)
          false
        }
      },
      defaultValue = false
    )
  }

  fun opChapterTitleFor(position: PlayerPosition): String {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          val book = r.audioBook
          val item =
            book.tableOfContents.lookupTOCItem(
              id = position.readingOrderID,
              offset = position.offsetMilliseconds
            )
          item.title
        } catch (e: Throwable) {
          this.logger.debug("opChapterTitleFor: ", e)
          ""
        }
      },
      defaultValue = ""
    )
  }

  fun opDownloadAll() {
    this.withPlayerIfPresent { r ->
      try {
        r.audioBook.wholeBookDownloadTask.fetch()
      } catch (e: Throwable) {
        this.logger.debug("opDownloadAll: ", e)
      }
    }
  }

  fun opDownloadProgress(): PlayerDownloadProgress {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          val tasks = r.audioBook.downloadTasks
          val totalPossible =
            tasks.sumOf { task -> 1.0 }
          val totalAchieved =
            tasks.sumOf { task -> task.progress.value }

          PlayerDownloadProgress.normalClamp(totalAchieved / totalPossible)
        } catch (e: Throwable) {
          this.logger.debug("opDownloadProgress: ", e)
          PlayerDownloadProgress(0.0)
        }
      },
      defaultValue = PlayerDownloadProgress(0.0)
    )
  }

  fun opFindDownloadingProgressIfAny(): PlayerDownloadProgress? {
    return this.withPlayerIfPresentElse(
      op = { r ->
        val tasks = r.audioBook.downloadTasks
        for (task in tasks) {
          when (val st = task.status) {
            is PlayerDownloadTaskStatus.Downloading -> {
              val progress = st.progress
              if (progress != null) {
                return@withPlayerIfPresentElse progress
              }
            }

            is PlayerDownloadTaskStatus.Failed -> {
              // Ignore
            }

            PlayerDownloadTaskStatus.IdleDownloaded -> {
              // Ignore
            }

            PlayerDownloadTaskStatus.IdleNotDownloaded -> {
              // Ignore
            }
          }
        }
        null
      },
      defaultValue = null
    )
  }

  fun opFindDownloadingStatusIfAny(): PlayerDownloadTaskStatus.Downloading? {
    return this.withPlayerIfPresentElse(
      op = { r ->
        val tasks = r.audioBook.downloadTasks
        for (task in tasks) {
          when (val st = task.status) {
            is PlayerDownloadTaskStatus.Downloading -> {
              return@withPlayerIfPresentElse st
            }

            is PlayerDownloadTaskStatus.Failed -> {
              // Ignore
            }

            PlayerDownloadTaskStatus.IdleDownloaded -> {
              // Ignore
            }

            PlayerDownloadTaskStatus.IdleNotDownloaded -> {
              // Ignore
            }
          }
        }
        null
      },
      defaultValue = null
    )
  }

  fun opIsOpen(): Boolean {
    return this.withPlayerIfPresentElse(
      op = { r -> !r.player.isClosed },
      defaultValue = false
    )
  }

  fun opTableOfContent(): PlayerManifestTOC? {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          r.audioBook.tableOfContents
        } catch (e: Throwable) {
          this.logger.debug("opTableOfContent: ", e)
          null
        }
      }, defaultValue = null
    )
  }

  fun opReadingOrder(): List<PlayerReadingOrderItemType> {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          r.audioBook.readingOrder
        } catch (e: Throwable) {
          this.logger.debug("opReadingOrder: ", e)
          listOf()
        }
      }, defaultValue = listOf()
    )
  }

  fun opReadingOrderByID(): Map<PlayerManifestReadingOrderID, PlayerReadingOrderItemType> {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          r.audioBook.readingOrderByID
        } catch (e: Throwable) {
          this.logger.debug("opReadingOrder: ", e)
          mapOf()
        }
      }, defaultValue = mapOf()
    )
  }

  fun opDownloadTasksFailed(): List<PlayerDownloadTaskType> {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          val tasks = r.audioBook.downloadTasks
          tasks.filter { t -> t.status is PlayerDownloadTaskStatus.Failed }
        } catch (e: Throwable) {
          this.logger.debug("opDownloadTasksFailed: ", e)
          listOf()
        }
      }, defaultValue = listOf()
    )
  }

  fun opPlayerID(): UUID? {
    return this.withPlayerIfPresentElse(
      op = { r ->
        try {
          r.player.id
        } catch (e: Throwable) {
          this.logger.debug("opPlayerID: ", e)
          null
        }
      },
      defaultValue = null
    )
  }

  fun opChapterPrevious() {
    this.withPlayerIfPresent { r ->
      r.player.chapterPrevious()
    }
  }

  fun opChapterNext() {
    this.withPlayerIfPresent { r ->
      r.player.chapterNext()
    }
  }
}
