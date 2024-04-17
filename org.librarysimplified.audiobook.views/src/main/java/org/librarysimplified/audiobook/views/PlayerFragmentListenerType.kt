package org.librarysimplified.audiobook.views

import android.content.Intent
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.annotation.DrawableRes
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerType
import java.util.concurrent.ScheduledExecutorService

/**
 * The listener interface implemented by activities hosting the various fragments included with
 * this package.
 */

interface PlayerFragmentListenerType {

  /**
   * Called when the player wants access to a player instance. The player should be created once
   * by the hosting activity and the same instance should be returned here each time this method
   * is called.
   */

  fun onPlayerWantsPlayer(): PlayerType

  /**
   * A fragment has created an image view representing a book cover image. The receiver must
   * now do whatever work is required to load the actual cover image into the given image view.
   */

  fun onPlayerWantsCoverImage(view: ImageView)

  /**
   * A fragment requires a book cover to be loaded as a bitmap in order to run the given callback
   * with the obtained bitmap which will be used as the large icon of the audiobook player
   * notification
   */
  fun onPlayerNotificationWantsBookCover(onBookCoverLoaded: (Bitmap) -> Unit)

  /**
   * A fragment requires the drawable resource to be used as the small of the audiobook player
   * notification
   */
  @DrawableRes
  fun onPlayerNotificationWantsSmallIcon(): Int

  /**
   * A fragment requires the intent to be used to open the app from the audiobook player
   * notification
   */
  fun onPlayerNotificationWantsIntent(): Intent

  /**
   * A fragment wants to know the title of the audio book being played. The receiver must return
   * the title of the book.
   */

  fun onPlayerWantsTitle(): String

  /**
   * A fragment wants to know the name of the author(s) of the audio book being played. The
   * receiver must return the name(s).
   */

  fun onPlayerWantsAuthor(): String

  /**
   * The user wants to save a bookmark on the audiobook. The caller should return a player bookmark
   * from the player's current position
   */

  fun onPlayerShouldAddBookmark(playerBookmark: PlayerBookmark?)

  /**
   * The user has performed an action that requires that the TOC be opened. The caller should
   * load a fragment capable of displaying the TOC
   * (such as {@link org.librarysimplified.audiobook.views.PlayerTOCFragment}).
   */

  fun onPlayerTOCShouldOpen()

  /**
   * The loaded TOC fragment wants access to the audio book currently playing.
   */

  fun onPlayerTOCWantsBook(): PlayerAudioBookType

  /**
   * The loaded TOC fragment wants access to the audio book bookmarks.
   */

  fun onPlayerTOCWantsBookmarks(): List<PlayerBookmark>

  /**
   * The user wants to delete a bookmark.
   */

  fun onPlayerShouldDeleteBookmark(
    playerBookmark: PlayerBookmark,
    onDeleteOperationCompleted: (Boolean) -> Unit
  )

  /**
   * The user has closed the table of contents. The callee should remove the TOC fragment from
   * the hosting activity.
   */

  fun onPlayerTOCWantsClose()

  /**
   * The user has performed an action that requires that a playback rate selection dialog be opened.
   * The caller should load a fragment capable of displaying the rate selection menu
   * (such as {@link org.nypl.audiobook.demo.android.views.PlayerPlaybackRateFragment}).
   */

  fun onPlayerPlaybackRateShouldOpen()

  /**
   * The user has performed an action that implies the player to be closed and the app to return to
   * the previous screen
   */

  fun onPlayerShouldBeClosed()

  /**
   * The user has performed an action that requires that a sleep timer configuration dialog be opened.
   * The caller should load a fragment capable of displaying the configuration menu
   * (such as {@link org.librarysimplified.audiobook.views.PlayerSleepTimerFragment}).
   */

  fun onPlayerSleepTimerShouldOpen()

  /**
   * The player wants access to a scheduled executor on which it can submit short time-related
   * tasks.
   */

  fun onPlayerWantsScheduledExecutor(): ScheduledExecutorService
}
