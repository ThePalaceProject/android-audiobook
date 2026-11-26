package org.librarysimplified.audiobook.demo

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import org.librarysimplified.audiobook.time_tracking.PlayerTimeTracked
import org.librarysimplified.audiobook.views.PlayerBaseFragment
import org.librarysimplified.audiobook.views.PlayerBookmarkModel
import org.librarysimplified.audiobook.views.PlayerFragment
import org.librarysimplified.audiobook.views.PlayerMiniFragment
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
import org.librarysimplified.audiobook.views.PlayerSleepTimerFragment
import org.librarysimplified.audiobook.views.PlayerTOCFragment
import org.librarysimplified.audiobook.views.PlayerViewCommand
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewCoverImageChanged
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewErrorsDownloadOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationCloseAll
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationExpand
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationMinimize
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationPlaybackRateMenuOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationSleepMenuOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCClose
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCOpen
import org.librarysimplified.audiobook.views.bluetooth.PlayerBluetoothWatcher
import org.librarysimplified.audiobook.views.focus.PlayerFocusWatcher
import org.slf4j.LoggerFactory
import java.util.UUID

class ExamplePlayerActivity : AppCompatActivity(R.layout.example_player_activity) {
  private val logger =
    LoggerFactory.getLogger(ExamplePlayerActivity::class.java)

  private val backCallback =
    object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        handleBack()
      }
    }

  private var fragmentNow: Fragment = ExampleFragmentError()
  private var subscriptions: CompositeDisposable = CompositeDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    this.askForNotificationsPermission()
    this.applySystemBarInsets()
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

  /**
   * On Android 15 (API 35) and later the system mandates edge-to-edge display, so the window
   * content extends under the navigation bar. Apply the system-bar insets as padding to the root
   * view so the compact player clears the navigation bar.
   */
  private fun applySystemBarInsets() {
    if (Build.VERSION.SDK_INT >= 35) {
      val root = this.findViewById<View>(R.id.example_player_root)
      ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        insets
      }
    }
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.stateEvents.subscribe(this::onModelStateEvent))
    this.subscriptions.add(PlayerModel.viewCommands.subscribe(this::onPlayerViewCommand))
    this.subscriptions.add(PlayerModel.playerEvents.subscribe(this::onPlayerEvent))
    this.subscriptions.add(PlayerModel.timeTracker.timeSegments.subscribe(this::onTimeTracked))

    PlayerBluetoothWatcher.enable(ExampleApplication.application)
    PlayerFocusWatcher.enable(ExampleApplication.application)

    this.onBackPressedDispatcher.addCallback(
      this,
      this.backCallback
    )
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

  fun attachToOpenPlayer() {
    this.switchFragment(PlayerFragment())
  }

  private fun showMiniPlayer() {
    if (this.supportFragmentManager.findFragmentById(R.id.example_mini_player_holder) != null) {
      return
    }

    findViewById<View>(R.id.example_mini_player_holder).visibility = View.VISIBLE
    this.supportFragmentManager
      .beginTransaction()
      .replace(R.id.example_mini_player_holder, PlayerMiniFragment())
      .commit()
  }

  private fun hideMiniPlayer() {
    val fragment =
      this.supportFragmentManager.findFragmentById(R.id.example_mini_player_holder)
    if (fragment != null) {
      this.supportFragmentManager
        .beginTransaction()
        .remove(fragment)
        .commit()
    }
    findViewById<View>(R.id.example_mini_player_holder).visibility = View.GONE
  }

  private fun switchFragment(fragment: Fragment) {
    this.fragmentNow = fragment
    this.supportFragmentManager
      .beginTransaction()
      .replace(R.id.example_player_fragment_holder, fragment)
      .commit()
  }

  private fun popupFragment(fragment: DialogFragment) {
    fragment.show(this.supportFragmentManager, fragment.tag)
  }

  private fun handleBack() =
    when (val f = this.fragmentNow) {
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
        this.finish()
      }

      is PlayerBaseFragment -> {
        when (f) {
          is PlayerFragment -> {
            this.close()
          }

          is PlayerTOCFragment -> {
            this.switchFragment(PlayerFragment())
          }

          is PlayerMiniFragment -> {
            // The compact player is hosted in its own container, not the main fragment holder.
          }
        }
      }

      else -> {
        throw IllegalStateException("Unrecognized fragment: $f")
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

        PlayerUIThread.runOnUIThread {
          try {
            Toast
              .makeText(
                this,
                "" +
                  "Created bookmark",
                Toast.LENGTH_LONG
              ).show()
          } catch (e: Throwable) {
            // Don't care
          }
        }
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

        PlayerUIThread.runOnUIThread {
          try {
            Toast
              .makeText(
                this,
                "" +
                  "Deleted bookmark",
                Toast.LENGTH_LONG
              ).show()
          } catch (e: Throwable) {
            // Don't care
          }
        }
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
        this.hideMiniPlayer()
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
          authorizationHandler = ExampleAuthorizationHandler,
          bookCredentials = state.bookCredentials,
          bookSource = state.bookSource,
          context = ExampleApplication.application,
          fetchAll = true,
          httpClient = ExampleApplication.httpClient,
          playerID = UUID.randomUUID(),
          manifest = state.manifest,
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
              this.resources,
              R.drawable.example_cover
            )
          )
          PlayerModel.bookTitle = manifest.metadata.title
          PlayerModel.bookAuthor = "An Example Author."
        }

        val start = state.positionOnOpen
        if (start != null) {
          try {
            Toast
              .makeText(
                this,
                "" +
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

  private fun onPlayerViewCommand(command: PlayerViewCommand) =
    when (command) {
      PlayerViewNavigationTOCClose -> {
        this.switchFragment(PlayerFragment())
      }

      PlayerViewNavigationTOCOpen -> {
        this.switchFragment(PlayerTOCFragment())
      }

      PlayerViewNavigationPlaybackRateMenuOpen -> {
        // Nothing required.
      }

      PlayerViewNavigationSleepMenuOpen -> {
        this.popupFragment(PlayerSleepTimerFragment())
      }

      PlayerViewCoverImageChanged -> {
        // Nothing to do
      }

      PlayerViewNavigationCloseAll -> {
        AlertDialog
          .Builder(this)
          .setMessage("Stop playback?")
          .setNegativeButton("Cancel", null)
          .setPositiveButton("Stop") { _, _ ->
            this.close()
            this.switchFragment(ExampleFragmentSelectBook())
          }.show()
      }

      PlayerViewNavigationMinimize -> {
        // The book is left open; the full-player view is dismissed and the compact player
        // is shown.
        this.switchFragment(ExampleFragmentSelectBook())
        this.showMiniPlayer()
      }

      PlayerViewNavigationExpand -> {
        // The book is already open; this attaches to the full player and hides the compact
        // player.
        this.hideMiniPlayer()
        this.attachToOpenPlayer()
      }

      PlayerViewErrorsDownloadOpen -> {
        this.switchFragment(ExampleFragmentErrorDownload())
      }

      PlayerViewCommand.PlayerViewLoginOpen -> {
        PlayerUIThread.runOnUIThread {
          Toast
            .makeText(
              this,
              "In a real application, a login screen would have opened here.",
              Toast.LENGTH_LONG
            ).show()
        }
      }
    }

  private fun onTimeTracked(time: PlayerTimeTracked) {
    this.logger.debug("TimeTracked: {}", time)

    ExampleTimeTracking.timeSecondsTracked += time.duration.toSeconds()

    PlayerUIThread.runOnUIThread {
      try {
        Toast
          .makeText(
            this,
            "" +
              "Time tracked: ${time.duration.toSeconds()} (Total seconds: ${ExampleTimeTracking.timeSecondsTracked})",
            Toast.LENGTH_LONG
          ).show()
      } catch (e: Throwable) {
        // Don't care
      }
    }
  }
}
