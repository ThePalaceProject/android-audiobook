package org.librarysimplified.audiobook.views.focus

import android.app.Application
import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import org.librarysimplified.audiobook.api.PlayerPauseReason
import org.librarysimplified.audiobook.views.PlayerModel
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An audio focus watcher that will pause playback whenever audio focus is lost. This can
 * happen when, for example, the user receives a phone call when listening to an audiobook.
 */

object PlayerFocusWatcher {

  private lateinit var context: Application

  private val logger =
    LoggerFactory.getLogger(PlayerFocusWatcher::class.java)

  private val enabled =
    AtomicBoolean(false)
  private val wasPaused =
    AtomicBoolean(false)

  fun enable(newContext: Application) {
    this.context = newContext
    this.enabled.set(true)
    this.logger.debug("Audio focus watcher enabled.")

    val audioManager =
      context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val afChangeListener =
      AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
          AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
          AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            this.onShouldPause()
          }

          AudioManager.AUDIOFOCUS_GAIN -> {
            this.onShouldResume()
          }

          AudioManager.AUDIOFOCUS_LOSS -> {
            this.onShouldPause()
          }
        }
      }

    val focusRequest =
      AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setOnAudioFocusChangeListener(afChangeListener)
        .build()

    val result = audioManager.requestAudioFocus(focusRequest)
    if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      this.logger.warn("Audio focus request was denied: {}", result)
      this.enabled.set(false)
    }
  }

  fun disable() {
    this.enabled.set(false)
    this.logger.debug("Audio focus watcher disabled.")
  }

  private fun onShouldResume() {
    if (this.enabled.get()) {
      this.logger.debug("Audio focus changed: Resuming audio.")
      if (this.wasPaused.compareAndSet(true, false)) {
        this.logger.debug("Player was playing and therefore will be resumed.")
        PlayerModel.play()
      } else {
        this.logger.debug("Player was not playing and therefore will not be resumed.")
      }
    }
  }

  private fun onShouldPause() {
    if (this.enabled.get()) {
      this.logger.debug("Audio focus changed: Pausing audio.")
      this.wasPaused.set(PlayerModel.isBuffering || PlayerModel.isPlaying)
      PlayerModel.pause(PlayerPauseReason.PAUSE_REASON_AUDIO_FOCUS_LOST)
    }
  }
}
