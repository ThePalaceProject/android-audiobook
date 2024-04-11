package org.librarysimplified.audiobook.demo

import android.graphics.BitmapFactory
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import io.reactivex.disposables.CompositeDisposable
import org.joda.time.DateTime
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerAccessibilityEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterWaiting
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventCreateBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackBuffering
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPreparing
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackWaitingForAction
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.views.PlayerBaseFragment
import org.librarysimplified.audiobook.views.PlayerBookmarkModel
import org.librarysimplified.audiobook.views.PlayerFragment
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.audiobook.views.PlayerModelState
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerBookOpenFailed
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerClosed
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestDownloadFailed
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestInProgress
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestLicenseChecksFailed
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestOK
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestParseFailed
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerOpen
import org.librarysimplified.audiobook.views.PlayerPlaybackRateFragment
import org.librarysimplified.audiobook.views.PlayerSleepTimerFragment
import org.librarysimplified.audiobook.views.PlayerTOCFragment
import org.librarysimplified.audiobook.views.PlayerViewCommand
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewCoverImageChanged
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationPlaybackRateMenuOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationSleepMenuOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCClose
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCOpen

class ExamplePlayerActivity : AppCompatActivity(R.layout.example_player_activity) {

  private val playerExtensions: List<PlayerExtensionType> = listOf()
  private var fragmentNow: Fragment = ExampleFragmentError()
  private var subscriptions: CompositeDisposable = CompositeDisposable()

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.stateEvents.subscribe(this::onModelStateEvent))
    this.subscriptions.add(PlayerModel.viewCommands.subscribe(this::onPlayerViewCommand))
    this.subscriptions.add(PlayerModel.playerEvents.subscribe(this::onPlayerEvent))
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.dispose()
  }

  private fun switchFragment(fragment: Fragment) {
    this.fragmentNow = fragment
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.example_player_fragment_holder, fragment)
      .commit()
  }

  private fun popupFragment(fragment: DialogFragment) {
    fragment.show(this.supportFragmentManager, fragment.tag)
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    return when (val f = this.fragmentNow) {
      is ExampleFragmentError -> {
        PlayerModel.closeBookOrDismissError()
        Unit
      }

      is ExampleFragmentProgress -> {
        PlayerModel.closeBookOrDismissError()
        Unit
      }

      is ExampleFragmentSelectBook -> {
        super.onBackPressed()
      }

      is PlayerBaseFragment -> {
        when (f) {
          is PlayerFragment -> {
            PlayerModel.closeBookOrDismissError()
            Unit
          }

          is PlayerTOCFragment -> {
            this.switchFragment(PlayerFragment())
          }
        }
      }

      null -> {
        super.onBackPressed()
      }

      else -> {
        throw IllegalStateException("Unrecognized fragment: $f")
      }
    }
  }

  @UiThread
  private fun onPlayerEvent(event: PlayerEvent) {
    when (event) {
      is PlayerEventCreateBookmark -> {
        val bookId =
          PlayerModel.manifest().metadata.identifier
        val bookmarkDatabase =
          ExampleApplication.application.bookmarkDatabase

        bookmarkDatabase.bookmarkSave(
          bookId,
          PlayerBookmark(
            kind = event.kind,
            title = event.tocItem.title,
            date = DateTime.now(),
            readingOrderID = event.readingOrderItem.id,
            offsetMilliseconds = event.offsetMilliseconds
          )
        )

        PlayerBookmarkModel.setBookmarks(bookmarkDatabase.bookmarkList(bookId))
      }

      is PlayerEvent.PlayerEventDeleteBookmark -> {
        val bookId =
          PlayerModel.manifest().metadata.identifier
        val bookmarkDatabase =
          ExampleApplication.application.bookmarkDatabase

        bookmarkDatabase.bookmarkDelete(
          bookId,
          event.bookmark
        )
      }

      is PlayerEvent.PlayerEventError,
      PlayerEvent.PlayerEventManifestUpdated,
      is PlayerEvent.PlayerEventPlaybackRateChanged,
      is PlayerEventChapterCompleted,
      is PlayerEventChapterWaiting,
      is PlayerEventPlaybackPreparing,
      is PlayerEventPlaybackBuffering,
      is PlayerEventPlaybackPaused,
      is PlayerEventPlaybackProgressUpdate,
      is PlayerEventPlaybackStarted,
      is PlayerEventPlaybackStopped,
      is PlayerEventPlaybackWaitingForAction -> {
        // Nothing to do
      }

      is PlayerAccessibilityEvent -> {
        // Not implemented yet.
      }
    }
  }

  @UiThread
  private fun onModelStateEvent(state: PlayerModelState) {
    PlayerUIThread.checkIsUIThread()

    when (state) {
      is PlayerBookOpenFailed -> {
        this.switchFragment(ExampleFragmentError())
      }

      PlayerClosed -> {
        PlayerModel.setCoverImage(null)
        this.switchFragment(ExampleFragmentSelectBook())
      }

      is PlayerManifestDownloadFailed -> {
        this.switchFragment(ExampleFragmentError())
      }

      PlayerManifestLicenseChecksFailed -> {
        this.switchFragment(ExampleFragmentError())
      }

      is PlayerManifestOK -> {
        PlayerModel.openPlayerForManifest(
          context = ExampleApplication.application,
          userAgent = PlayerUserAgent("AudioBookDemo"),
          extensions = this.playerExtensions,
          manifest = state.manifest
        )
      }

      is PlayerManifestParseFailed -> {
        this.switchFragment(ExampleFragmentError())
      }

      is PlayerOpen -> {
        PlayerModel.setCoverImage(BitmapFactory.decodeResource(resources, R.drawable.example_cover))

        val bookId =
          PlayerModel.manifest().metadata.identifier
        val bookmarkDatabase =
          ExampleApplication.application.bookmarkDatabase
        val bookmarksAll =
          bookmarkDatabase.bookmarkList(bookId)
        val bookmarkLastRead =
          bookmarkDatabase.bookmarkFindLastRead(bookId)

        PlayerBookmarkModel.setBookmarks(bookmarksAll)
        if (bookmarkLastRead != null) {
          PlayerModel.movePlayheadTo(bookmarkLastRead.position)
        }

        this.switchFragment(PlayerFragment())
      }

      PlayerManifestInProgress -> {
        this.switchFragment(ExampleFragmentProgress())
      }
    }
  }

  private fun onPlayerViewCommand(command: PlayerViewCommand) {
    return when (command) {
      PlayerViewNavigationTOCClose -> {
        this.switchFragment(PlayerFragment())
      }

      PlayerViewNavigationTOCOpen -> {
        this.switchFragment(PlayerTOCFragment())
      }

      PlayerViewNavigationPlaybackRateMenuOpen -> {
        this.popupFragment(PlayerPlaybackRateFragment())
      }

      PlayerViewNavigationSleepMenuOpen -> {
        this.popupFragment(PlayerSleepTimerFragment())
      }

      PlayerViewCoverImageChanged -> {
        // Nothing to do
      }
    }
  }
}
