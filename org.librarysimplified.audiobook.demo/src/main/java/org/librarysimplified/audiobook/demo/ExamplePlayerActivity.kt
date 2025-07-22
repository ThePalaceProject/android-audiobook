package org.librarysimplified.audiobook.demo

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import io.reactivex.disposables.CompositeDisposable
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
import org.librarysimplified.audiobook.time_tracking.PlayerTimeTracked
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
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewErrorsDownloadOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationCloseAll
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationPlaybackRateMenuOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationSleepMenuOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCClose
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCOpen
import org.slf4j.LoggerFactory

class ExamplePlayerActivity : AppCompatActivity(R.layout.example_player_activity) {

  private val logger =
    LoggerFactory.getLogger(ExamplePlayerActivity::class.java)

  private var fragmentNow: Fragment = ExampleFragmentError()
  private var subscriptions: CompositeDisposable = CompositeDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    this.askForNotificationsPermission()
  }

  private fun askForNotificationsPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
        100
      )
    }
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.stateEvents.subscribe(this::onModelStateEvent))
    this.subscriptions.add(PlayerModel.viewCommands.subscribe(this::onPlayerViewCommand))
    this.subscriptions.add(PlayerModel.playerEvents.subscribe(this::onPlayerEvent))
    this.subscriptions.add(PlayerModel.timeTracker.timeSegments.subscribe(this::onTimeTracked))
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.dispose()
  }

  private fun close() {
    try {
      PlayerModel.closeBookOrDismissError()
    } catch (e: Exception) {
      this.logger.error("Failed to close book: ", e)
    }
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
        this.close()
      }

      is ExampleFragmentErrorDownload -> {
        this.switchFragment(PlayerTOCFragment())
      }

      is ExampleFragmentProgress -> {
        this.close()
      }

      is ExampleFragmentSelectBook -> {
        super.onBackPressed()
      }

      is PlayerBaseFragment -> {
        when (f) {
          is PlayerFragment -> {
            this.close()
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
        val manifest =
          PlayerModel.manifest() ?: return

        val bookId =
          manifest.metadata.identifier
        val bookmarkDatabase =
          ExampleApplication.application.bookmarkDatabase

        bookmarkDatabase.bookmarkSave(
          bookId,
          PlayerBookmark(
            kind = event.kind,
            readingOrderID = event.readingOrderItem.id,
            offsetMilliseconds = event.readingOrderItemOffsetMilliseconds,
            metadata = event.bookmarkMetadata
          )
        )

        PlayerBookmarkModel.setBookmarks(bookmarkDatabase.bookmarkList(bookId))
      }

      is PlayerEvent.PlayerEventDeleteBookmark -> {
        val manifest =
          PlayerModel.manifest() ?: return

        val bookId =
          manifest.metadata.identifier
        val bookmarkDatabase =
          ExampleApplication.application.bookmarkDatabase

        bookmarkDatabase.bookmarkDelete(
          bookId,
          event.bookmark
        )
      }

      is PlayerEvent.PlayerEventError,
      is PlayerEvent.PlayerEventManifestUpdated,
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

      is PlayerManifestLicenseChecksFailed -> {
        this.switchFragment(ExampleFragmentError())
      }

      is PlayerManifestOK -> {
        val bookId =
          state.manifest.metadata.identifier
        val bookmarkDatabase =
          ExampleApplication.application.bookmarkDatabase
        val bookmarksAll =
          bookmarkDatabase.bookmarkList(bookId)

        PlayerBookmarkModel.setBookmarks(bookmarksAll)

        PlayerModel.openPlayerForManifest(
          context = ExampleApplication.application,
          userAgent = PlayerUserAgent("AudioBookDemo"),
          manifest = state.manifest,
          fetchAll = true,
          bookSource = state.bookSource,
          bookCredentials = state.bookCredentials
        )
      }

      is PlayerManifestParseFailed -> {
        this.switchFragment(ExampleFragmentError())
      }

      is PlayerOpen -> {
        val manifest = PlayerModel.manifest()
        if (manifest != null) {
          PlayerModel.setCoverImage(
            BitmapFactory.decodeResource(
              resources,
              R.drawable.example_cover
            )
          )
          PlayerModel.bookTitle = manifest.metadata.title
          PlayerModel.bookAuthor = "An Example Author."
        }

        val start = state.positionOnOpen
        if (start != null) {
          try {
            Toast.makeText(this, "" +
              "Starting at saved position: ${start.readingOrderID.text} ${start.offsetMilliseconds.value}",
              Toast.LENGTH_LONG
            ).show()
          } catch (e: Throwable) {
            // Don't care
          }
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

      PlayerViewNavigationCloseAll -> {
        this.close()
        this.switchFragment(ExampleFragmentSelectBook())
      }

      PlayerViewErrorsDownloadOpen -> {
        this.switchFragment(ExampleFragmentErrorDownload())
      }
    }
  }

  private fun onTimeTracked(
    time: PlayerTimeTracked
  ) {
    this.logger.debug("TimeTracked: {}", time)

    ExampleTimeTracking.timeSecondsTracked += time.duration.toSeconds()

    PlayerUIThread.runOnUIThread {
      try {
        Toast.makeText(this, "" +
          "Time tracked: ${time.duration.toSeconds()} (Total seconds: ${ExampleTimeTracking.timeSecondsTracked})",
          Toast.LENGTH_LONG
        ).show()
      } catch (e: Throwable) {
        // Don't care
      }
    }
  }
}
