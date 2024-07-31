package org.librarysimplified.audiobook.api

/**
 * The normalized download progress in the range [0, 1].
 */

data class PlayerDownloadProgress(
  val value: Double
) {
  fun asPercent(): Int {
    return (this.value * 100.0).toInt()
  }

  init {
    require(this.value >= 0.0) { "Value ${this.value} must be in the range [0, 1]" }
    require(this.value <= 1.0) { "Value ${this.value} must be in the range [0, 1]" }
  }

  companion object {
    fun percentClamp(
      percent: Int
    ): PlayerDownloadProgress {
      return this.normalClamp(percent.toDouble() / 100.0)
    }

    fun normalClamp(
      value: Double
    ): PlayerDownloadProgress {
      return PlayerDownloadProgress(Math.min(1.0, Math.max(0.0, value)))
    }
  }
}
