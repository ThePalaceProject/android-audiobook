package org.librarysimplified.audiobook.views

import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.slf4j.LoggerFactory

object PlayerBookmarkModel {

  private val logger =
    LoggerFactory.getLogger(PlayerBookmarkModel::class.java)

  @Volatile
  private var bookmarksNow: List<PlayerBookmark> = listOf<PlayerBookmark>()

  private val bookmarksSubject: Subject<List<PlayerBookmark>> =
    BehaviorSubject.create<List<PlayerBookmark>>()
      .toSerialized()

  /**
   * A source of bookmark events that is always observed on the UI thread.
   */

  val bookmarkEvents: Observable<List<PlayerBookmark>> =
    this.bookmarksSubject.observeOn(AndroidSchedulers.mainThread())

  /**
   * Set the current bookmarks.
   */

  fun setBookmarks(newBookmarks: List<PlayerBookmark>) {
    this.bookmarksNow = newBookmarks.toList()

    if (this.bookmarksNow.isEmpty()) {
      this.logger.debug("setBookmarks: Bookmark list is now empty.")
    } else {
      this.bookmarksNow.forEachIndexed { index, playerBookmark ->
        this.logger.debug("setBookmarks: [{}] {}", index, playerBookmark)
      }
    }

    this.bookmarksSubject.onNext(this.bookmarksNow)
  }

  /**
   * Clear the current bookmarks.
   */

  fun clearBookmarks() {
    this.bookmarksNow = listOf()
    this.bookmarksSubject.onNext(this.bookmarksNow)
  }

  /**
   * List the current bookmarks.
   */

  fun bookmarks(): List<PlayerBookmark> {
    return this.bookmarksNow.toList()
  }
}
