package org.librarysimplified.audiobook.api

import org.joda.time.Duration
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCItem

/**
 * The type of events signalled by the player.
 */

sealed class PlayerEvent {

  /**
   * The player's playback rate changed.
   */

  data class PlayerEventPlaybackRateChanged(
    val rate: PlayerPlaybackRate
  ) : PlayerEvent()

  /**
   * The player's manifest was successfully updated.
   */

  data object PlayerEventManifestUpdated : PlayerEvent()

  /**
   * An error occurred during playback. The error is expected to reflect an error in the
   * underlying audio engine and, as such, the specifics of the errors themselves are vague.
   */

  data class PlayerEventError(
    val readingOrderItem: PlayerReadingOrderItemType?,
    val offsetMilliseconds: Long,
    val exception: java.lang.Exception?,
    val errorCode: Int
  ) : PlayerEvent()

  sealed class PlayerEventWithPosition : PlayerEvent() {

    /**
     * The reading order item to which this event refers.
     */

    abstract val readingOrderItem: PlayerReadingOrderItemType

    /**
     * The table of contents item within which the current position falls
     */

    abstract val tocItem: PlayerManifestTOCItem

    abstract val totalRemainingBookTime: Duration

    /**
     * Playback of the given reading order item has started.
     */

    data class PlayerEventPlaybackStarted(
      override val readingOrderItem: PlayerReadingOrderItemType,
      val offsetMilliseconds: Long,
      override val tocItem: PlayerManifestTOCItem,
      override val totalRemainingBookTime: Duration
    ) : PlayerEventWithPosition()

    /**
     * Playback is currently buffering for the given reading order item. This can happen at any time
     * during playback if the given spine item has not been downloaded.
     */

    data class PlayerEventPlaybackBuffering(
      override val readingOrderItem: PlayerReadingOrderItemType,
      val offsetMilliseconds: Long,
      override val tocItem: PlayerManifestTOCItem,
      override val totalRemainingBookTime: Duration
    ) : PlayerEventWithPosition()

    /**
     * The given spine item is ready to be played, but waiting for the user's action since it
     * won't automatically start playing.
     */

    data class PlayerEventPlaybackWaitingForAction(
      override val readingOrderItem: PlayerReadingOrderItemType,
      val offsetMilliseconds: Long,
      override val tocItem: PlayerManifestTOCItem,
      override val totalRemainingBookTime: Duration
    ) : PlayerEventWithPosition()

    /**
     * The given spine item is playing, and this event is a progress update indicating how far
     * along playback is.
     */

    data class PlayerEventPlaybackProgressUpdate(
      override val readingOrderItem: PlayerReadingOrderItemType,
      val offsetMilliseconds: Long,
      override val tocItem: PlayerManifestTOCItem,
      override val totalRemainingBookTime: Duration
    ) : PlayerEventWithPosition()

    /**
     * Playback of the given reading order item has just completed, and playback will continue to the
     * next spine item if it is available (downloaded).
     */

    data class PlayerEventChapterCompleted(
      override val readingOrderItem: PlayerReadingOrderItemType,
      override val tocItem: PlayerManifestTOCItem,
      override val totalRemainingBookTime: Duration
    ) : PlayerEventWithPosition()

    /**
     * The player is currently waiting for the given reading order item to become available before
     * playback can continue.
     */

    data class PlayerEventChapterWaiting(
      override val readingOrderItem: PlayerReadingOrderItemType,
      override val tocItem: PlayerManifestTOCItem,
      override val totalRemainingBookTime: Duration
    ) : PlayerEventWithPosition()

    /**
     * Playback of the given reading order item has paused.
     */

    data class PlayerEventPlaybackPaused(
      override val readingOrderItem: PlayerReadingOrderItemType,
      val offsetMilliseconds: Long,
      override val tocItem: PlayerManifestTOCItem,
      override val totalRemainingBookTime: Duration
    ) : PlayerEventWithPosition()

    /**
     * Playback of the given reading order item has stopped.
     */

    data class PlayerEventPlaybackStopped(
      override val readingOrderItem: PlayerReadingOrderItemType,
      val offsetMilliseconds: Long,
      override val tocItem: PlayerManifestTOCItem,
      override val totalRemainingBookTime: Duration
    ) : PlayerEventWithPosition()

    /**
     * Playback has progressed in a manner that's significant enough to justify a new
     * bookmark. This is typically on chapter changes, and periodically when playing
     * a chapter.
     */

    data class PlayerEventCreateBookmark(
      override val readingOrderItem: PlayerReadingOrderItemType,
      val offsetMilliseconds: Long,
      override val tocItem: PlayerManifestTOCItem,
      override val totalRemainingBookTime: Duration,
      val kind: PlayerBookmarkKind
    ) : PlayerEventWithPosition()
  }

  /**
   * The type of events significant to accessibility.
   */

  sealed class PlayerAccessibilityEvent : PlayerEvent() {

    /**
     * A localized accessibility message suitable for direct use with a screen reader.
     */

    abstract val message: String

    /**
     * The player has been buffering for a length of time significant enough to warrant
     * announcing to the user.
     */

    data class PlayerAccessibilityIsBuffering(
      override val message: String
    ) : PlayerAccessibilityEvent()

    /**
     * The player cannot continue until the target chapter has been downloaded.
     */

    data class PlayerAccessibilityIsWaitingForChapter(
      override val message: String
    ) : PlayerAccessibilityEvent()

    /**
     * The player has published an error significant enough to warrant announcing to the user.
     */

    data class PlayerAccessibilityErrorOccurred(
      override val message: String
    ) : PlayerAccessibilityEvent()

    /**
     * A new playback rate has been selected.
     */

    data class PlayerAccessibilityPlaybackRateChanged(
      override val message: String
    ) : PlayerAccessibilityEvent()

    /**
     * A new sleep timer setting has been selected.
     */

    data class PlayerAccessibilitySleepTimerSettingChanged(
      override val message: String
    ) : PlayerAccessibilityEvent()

    /**
     * A chapter has been selected.
     */

    data class PlayerAccessibilityChapterSelected(
      override val message: String
    ) : PlayerAccessibilityEvent()
  }
}
