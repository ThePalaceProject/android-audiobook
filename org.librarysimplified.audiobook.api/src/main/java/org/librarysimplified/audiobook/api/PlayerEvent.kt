package org.librarysimplified.audiobook.api

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

  object PlayerEventManifestUpdated : PlayerEvent()

  /**
   * An error occurred during playback. The error is expected to reflect an error in the
   * underlying audio engine and, as such, the specifics of the errors themselves are vague.
   */

  data class PlayerEventError(
    val spineElement: PlayerSpineElementType?,
    val offsetMilliseconds: Long,
    val exception: java.lang.Exception?,
    val errorCode: Int
  ) : PlayerEvent()

  sealed class PlayerEventWithSpineElement : PlayerEvent() {

    /**
     * The spine element to which this event refers.
     */

    abstract val spineElement: PlayerSpineElementType

    /**
     * Playback of the given spine element has started.
     */

    data class PlayerEventPlaybackStarted(
      override val spineElement: PlayerSpineElementType,
      val offsetMilliseconds: Long
    ) : PlayerEventWithSpineElement()

    /**
     * Playback is currently buffering for the given spine element. This can happen at any time
     * during playback if the given spine item has not been downloaded.
     */

    data class PlayerEventPlaybackBuffering(
      override val spineElement: PlayerSpineElementType,
      val offsetMilliseconds: Long
    ) : PlayerEventWithSpineElement()

    /**
     * The given spine item is ready to be played, but waiting for the user's action since it
     * won't automatically start playing.
     */

    data class PlayerEventPlaybackWaitingForAction(
      override val spineElement: PlayerSpineElementType,
      val offsetMilliseconds: Long
    ) : PlayerEventWithSpineElement()

    /**
     * The given spine item is playing, and this event is a progress update indicating how far
     * along playback is.
     */

    data class PlayerEventPlaybackProgressUpdate(
      override val spineElement: PlayerSpineElementType,
      val offsetMilliseconds: Long
    ) : PlayerEventWithSpineElement()

    /**
     * Playback of the given spine element has just completed, and playback will continue to the
     * next spine item if it is available (downloaded).
     */

    data class PlayerEventChapterCompleted(
      override val spineElement: PlayerSpineElementType
    ) : PlayerEventWithSpineElement()

    /**
     * The player is currently waiting for the given spine element to become available before
     * playback can continue.
     */

    data class PlayerEventChapterWaiting(
      override val spineElement: PlayerSpineElementType
    ) : PlayerEventWithSpineElement()

    /**
     * Playback of the given spine element has paused.
     */

    data class PlayerEventPlaybackPaused(
      override val spineElement: PlayerSpineElementType,
      val offsetMilliseconds: Long
    ) : PlayerEventWithSpineElement()

    /**
     * Playback of the given spine element has stopped.
     */

    data class PlayerEventPlaybackStopped(
      override val spineElement: PlayerSpineElementType,
      val offsetMilliseconds: Long
    ) : PlayerEventWithSpineElement()

    /**
     * Playback has progressed in a manner that's significant enough to justify a new
     * bookmark. This is typically on chapter changes, and periodically when playing
     * a chapter.
     */

    data class PlayerEventCreateBookmark(
      override val spineElement: PlayerSpineElementType,
      val offsetMilliseconds: Long,
      val isLocalBookmark: Boolean
    ) : PlayerEventWithSpineElement()
  }
}
