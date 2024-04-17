package org.librarysimplified.audiobook.views

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

class PlayerMediaReceiver(
  private val onAudioBecomingNoisy: () -> Unit
) : BroadcastReceiver() {

  override fun onReceive(context: Context?, intent: Intent?) {
    if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
      this.onAudioBecomingNoisy()
    }
  }
}
