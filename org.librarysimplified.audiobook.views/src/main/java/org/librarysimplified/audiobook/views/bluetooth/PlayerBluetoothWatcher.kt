package org.librarysimplified.audiobook.views.bluetooth

import android.app.Application
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerAccessibilityEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventDeleteBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventError
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventManifestUpdated
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition
import org.librarysimplified.audiobook.views.PlayerModel
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

object PlayerBluetoothWatcher {

  private val logger =
    LoggerFactory.getLogger(PlayerBluetoothWatcher::class.java)

  private val enabled =
    AtomicBoolean(false)

  @Volatile
  private var context: Application? = null

  private val devicesConnectedThen: MutableSet<String> = mutableSetOf()

  init {
    PlayerModel.playerEvents.subscribe { event ->
      try {
        this.onPlayerEvent(event)
      } catch (e: Throwable) {
        this.logger.debug("Event handling exception: ", e)
      }
    }
  }

  fun enable(newContext: Application) {
    this.context = newContext
    this.enabled.set(true)
    this.logger.debug("Bluetooth watcher enabled.")
  }

  fun disable() {
    this.enabled.set(false)
    this.logger.debug("Bluetooth watcher disabled.")
  }

  private fun onPlayerEvent(
    event: PlayerEvent
  ) {
    if (!this.enabled.get()) {
      return
    }

    return when (event) {
      is PlayerAccessibilityEvent.PlayerAccessibilityChapterSelected,
      is PlayerAccessibilityEvent.PlayerAccessibilityErrorOccurred,
      is PlayerAccessibilityEvent.PlayerAccessibilityIsBuffering,
      is PlayerAccessibilityEvent.PlayerAccessibilityIsWaitingForChapter,
      is PlayerAccessibilityEvent.PlayerAccessibilityPlaybackRateChanged,
      is PlayerAccessibilityEvent.PlayerAccessibilitySleepTimerSettingChanged,
      is PlayerEventDeleteBookmark,
      is PlayerEventError,
      is PlayerEventManifestUpdated,
      is PlayerEventPlaybackRateChanged,
      is PlayerEventWithPosition.PlayerEventChapterCompleted,
      is PlayerEventWithPosition.PlayerEventChapterWaiting,
      is PlayerEventWithPosition.PlayerEventCreateBookmark,
      is PlayerEventWithPosition.PlayerEventPlaybackBuffering,
      is PlayerEventWithPosition.PlayerEventPlaybackPaused,
      is PlayerEventWithPosition.PlayerEventPlaybackPreparing,
      is PlayerEventWithPosition.PlayerEventPlaybackStarted,
      is PlayerEventWithPosition.PlayerEventPlaybackStopped,
      is PlayerEventWithPosition.PlayerEventPlaybackWaitingForAction -> {
        // Nothing to do.
      }

      is PlayerEventWithPosition.PlayerEventPlaybackProgressUpdate -> {
        if (this.bluetoothAudioWasDisconnected()) {
          this.logger.debug("Bluetooth audio disconnected. Pausing player...")
          PlayerModel.pause()
        } else {
          // Nothing to do.
        }
      }
    }
  }

  /**
   * The Android documentation will recommend all kinds of complicated schemes involving
   * subscribing to various intents with broadcast receivers in order to track device
   * connections and disconnections. Then, in reality, none of those intents will ever actually
   * reach the application.
   *
   * Instead, we simply track which devices are connected each time the player publishes a
   * position update, and we pause audio if the number of bluetooth audio devices has decreased.
   * It's simple and stupid and actually works.
   */

  private fun bluetoothAudioWasDisconnected(): Boolean {
    val contextNow =
      this.context ?: return false

    val audioManager =
      contextNow.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices =
      audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

    val devicesConnectedNow =
      mutableSetOf<String>()

    for (device in devices) {
      if (isBluetooth(device)) {
        devicesConnectedNow.add(device.address)
      }
    }

    val wasDisconnected = devicesConnectedNow.size < devicesConnectedThen.size
    this.devicesConnectedThen.clear()
    this.devicesConnectedThen.addAll(devicesConnectedNow)
    return wasDisconnected
  }

  private fun isBluetooth(
    device: AudioDeviceInfo
  ): Boolean {
    val type = device.type
    return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
  }
}
