package org.librarysimplified.audiobook.open_access

import org.slf4j.LoggerFactory

/**
 * Some underlying audio engines on some devices will fail to play tracks, but won't actually
 * return errors. Instead, the only visible symptom from the code is that all tracks will appear
 * to have a duration of zero. This class exists to log a message if the player determines that
 * all tracks have a zero duration.
 */

class ExoPlayerZeroBugDetection(
  private val tracks: List<ExoSpineElement>
) {
  private val logger = LoggerFactory.getLogger(ExoPlayerZeroBugDetection::class.java)
  private val trackDurations: MutableMap<Int, Long> = mutableMapOf()
  private var logged = false

  fun recordTrackDuration(
    index: Int,
    duration: Long
  ) {
    try {
      this.trackDurations[index] = duration
      if (this.trackDurations.size == this.tracks.size) {
        if (!this.logged) {
          this.logger.error(
            "Possible audio engine bug: All {} tracks have a zero duration!",
            this.trackDurations.size
          )
        }
      }
    } catch (e: Throwable) {
      // Ignore everything
    }
  }
}
