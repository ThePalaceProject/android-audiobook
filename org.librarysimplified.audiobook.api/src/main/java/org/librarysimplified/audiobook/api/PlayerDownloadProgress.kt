package org.librarysimplified.audiobook.api

/**
 * The normalized download progress in the range [0, 1].
 */

data class PlayerDownloadProgress(
  val value: Double
) {
  fun asPercent(): Int = (this.value * 100.0).toInt()

  init {
    require(this.value >= 0.0) { "Value ${this.value} must be in the range [0, 1]" }
    require(this.value <= 1.0) { "Value ${this.value} must be in the range [0, 1]" }
  }

  companion object {
    fun percentClamp(percent: Int): PlayerDownloadProgress = this.normalClamp(percent.toDouble() / 100.0)

    fun normalClamp(value: Double): PlayerDownloadProgress = PlayerDownloadProgress(Math.min(1.0, Math.max(0.0, value)))
  }
}
