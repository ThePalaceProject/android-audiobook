package org.librarysimplified.audiobook.demo

import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.views.PlayerFragment2
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

class ExamplePlayerActivity : AppCompatActivity(R.layout.example_player_activity) {

  private val playerExtensions: List<PlayerExtensionType> = listOf()
  private var fragmentNow: Fragment = ExampleFragmentError()
  private var subscriptions: CompositeDisposable = CompositeDisposable()

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.stateEvents.subscribe(this::onModelStateEvent))
    this.switchFragment(ExampleFragmentSelectBook())
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

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    when (PlayerModel.state) {
      PlayerClosed -> {
        super.onBackPressed()
      }

      is PlayerBookOpenFailed,
      is PlayerManifestDownloadFailed,
      PlayerManifestInProgress,
      PlayerManifestLicenseChecksFailed,
      is PlayerManifestOK,
      is PlayerManifestParseFailed,
      is PlayerOpen -> {
        PlayerModel.closeBookOrDismissError()
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
        this.switchFragment(PlayerFragment2())
      }

      PlayerManifestInProgress -> {
        this.switchFragment(ExampleFragmentProgress())
      }
    }
  }
}
