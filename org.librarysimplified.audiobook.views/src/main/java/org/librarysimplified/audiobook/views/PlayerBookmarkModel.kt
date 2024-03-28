package org.librarysimplified.audiobook.views

import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.audiobook.api.PlayerBookmark

object PlayerBookmarkModel {

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
