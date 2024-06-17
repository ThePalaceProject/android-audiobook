package org.librarysimplified.audiobook.views.mediacontrols

import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Size
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerAccessibilityEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventDeleteBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventError
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventManifestUpdated
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
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
import org.librarysimplified.audiobook.views.PlayerModel
import org.slf4j.LoggerFactory

/**
 * The media session API requires access to a [Player] instance.
 *
 * Unfortunately, at least one of the player implementations we provide does not work in terms of
 * a [Player]. This facade simply forwards various operations to the [PlayerModel]. Most of the
 * operations do nothing as they have no corresponding functionality in our real underlying
 * players.
 */

object PlayerMediaFacade : Player {

  private val logger =
    LoggerFactory.getLogger(PlayerMediaFacade::class.java)

  private val supportedCommands =
    Player.Commands.Builder()
      .add(Player.COMMAND_PLAY_PAUSE)
      .build()

  @Volatile
  private var latestException: PlaybackException? = null

  @Volatile
  private var listeners: List<Player.Listener> = listOf()

  init {
    PlayerModel.playerEvents.subscribe(this::onPlayerEvent)
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    return when (event) {
      is PlayerAccessibilityEvent -> {
        // Nothing to do
      }

      is PlayerEventDeleteBookmark -> {
        // Nothing to do
      }

      is PlayerEventError -> {
        val exception =
          PlaybackException(
            "Playback error: ${event.errorCode}",
            event.exception,
            event.errorCode
          )

        this.latestException = exception
        this.listeners.forEach { listener ->
          listener.onPlayerError(exception)
        }
      }

      PlayerEventManifestUpdated -> {
        // Nothing to do
      }

      is PlayerEventPlaybackRateChanged -> {
        // Nothing to do
      }

      is PlayerEventChapterCompleted -> {
        // Nothing to do
      }

      is PlayerEventChapterWaiting -> {
        // Nothing to do
      }

      is PlayerEventCreateBookmark -> {
        // Nothing to do
      }

      is PlayerEventPlaybackBuffering -> {
        // Nothing to do
      }

      is PlayerEventPlaybackPreparing -> {
        // Nothing to do
      }

      is PlayerEventPlaybackProgressUpdate -> {
        // Nothing to do
      }

      is PlayerEventPlaybackStarted -> {
        this.listeners.forEach { listener ->
          listener.onIsPlayingChanged(true)
        }
      }

      is PlayerEventPlaybackPaused,
      is PlayerEventPlaybackStopped -> {
        this.listeners.forEach { listener ->
          listener.onIsPlayingChanged(false)
        }
      }

      is PlayerEventPlaybackWaitingForAction -> {
        // Nothing to do
      }
    }
  }

  private fun warnNotImplemented(
    name: String
  ) {
    this.logger.warn("[{}] Facade method does nothing", name)
  }

  override fun getApplicationLooper(): Looper {
    return Looper.getMainLooper()
  }

  override fun addListener(
    listener: Player.Listener
  ) {
    this.logger.debug("addListener: {}", listener)
    this.listeners = this.listeners.plus(listener)
    this.logger.debug("addListener: {} listeners now", this.listeners.size)
  }

  override fun removeListener(
    listener: Player.Listener
  ) {
    this.logger.debug("removeListener: {}", listener)
    this.listeners = this.listeners.minus(listener)
    this.logger.debug("removeListener: {} listeners now", this.listeners.size)
  }

  override fun setMediaItems(
    mediaItems: MutableList<MediaItem>
  ) {
    this.warnNotImplemented("setMediaItems")
  }

  override fun setMediaItems(
    mediaItems: MutableList<MediaItem>,
    resetPosition: Boolean
  ) {
    this.warnNotImplemented("setMediaItems")
  }

  override fun setMediaItems(
    mediaItems: MutableList<MediaItem>,
    startIndex: Int,
    startPositionMs: Long
  ) {
    this.warnNotImplemented("setMediaItems")
  }

  override fun setMediaItem(
    mediaItem: MediaItem
  ) {
    this.warnNotImplemented("setMediaItem")
  }

  override fun setMediaItem(
    mediaItem: MediaItem,
    startPositionMs: Long
  ) {
    this.warnNotImplemented("setMediaItem")
  }

  override fun setMediaItem(
    mediaItem: MediaItem,
    resetPosition: Boolean
  ) {
    this.warnNotImplemented("setMediaItem")
  }

  override fun addMediaItem(
    mediaItem: MediaItem
  ) {
    this.warnNotImplemented("addMediaItem")
  }

  override fun addMediaItem(
    index: Int,
    mediaItem: MediaItem
  ) {
    this.warnNotImplemented("addMediaItem")
  }

  override fun addMediaItems(
    mediaItems: MutableList<MediaItem>
  ) {
    this.warnNotImplemented("addMediaItems")
  }

  override fun addMediaItems(
    index: Int,
    mediaItems: MutableList<MediaItem>
  ) {
    this.warnNotImplemented("addMediaItems")
  }

  override fun moveMediaItem(
    currentIndex: Int,
    newIndex: Int
  ) {
    this.warnNotImplemented("moveMediaItem")
  }

  override fun moveMediaItems(
    fromIndex: Int,
    toIndex: Int,
    newIndex: Int
  ) {
    this.warnNotImplemented("moveMediaItems")
  }

  override fun replaceMediaItem(
    index: Int,
    mediaItem: MediaItem
  ) {
    this.warnNotImplemented("replaceMediaItem")
  }

  override fun replaceMediaItems(
    fromIndex: Int,
    toIndex: Int,
    mediaItems: MutableList<MediaItem>
  ) {
    this.warnNotImplemented("replaceMediaItems")
  }

  override fun removeMediaItem(
    index: Int
  ) {
    this.warnNotImplemented("removeMediaItem")
  }

  override fun removeMediaItems(
    fromIndex: Int,
    toIndex: Int
  ) {
    this.warnNotImplemented("removeMediaItems")
  }

  override fun clearMediaItems() {
    this.warnNotImplemented("clearMediaItems")
  }

  override fun isCommandAvailable(
    command: Int
  ): Boolean {
    return this.supportedCommands.contains(command)
  }

  override fun canAdvertiseSession(): Boolean {
    this.logger.info("canAdvertiseSession: returning true")
    return true
  }

  override fun getAvailableCommands(): Player.Commands {
    return this.supportedCommands
  }

  override fun prepare() {
    this.warnNotImplemented("prepare")
  }

  override fun getPlaybackState(): Int {
    return if (PlayerModel.isPlaying) {
      Player.STATE_READY
    } else {
      Player.STATE_IDLE
    }
  }

  override fun getPlaybackSuppressionReason(): Int {
    this.warnNotImplemented("getPlaybackSuppressionReason")
    return Player.PLAYBACK_SUPPRESSION_REASON_NONE
  }

  override fun isPlaying(): Boolean {
    return PlayerModel.isPlaying
  }

  override fun getPlayerError(): PlaybackException? {
    val existing = this.latestException
    this.latestException = null
    return existing
  }

  override fun play() {
    this.logger.debug("play")
    PlayerModel.play()
  }

  override fun pause() {
    this.logger.debug("pause")
    PlayerModel.pause()
  }

  override fun setPlayWhenReady(
    playWhenReady: Boolean
  ) {
    this.logger.debug("setPlayWhenReady {}", playWhenReady)
    if (playWhenReady) {
      this.play()
    } else {
      this.pause()
    }
  }

  override fun getPlayWhenReady(): Boolean {
    return PlayerModel.isPlaying
  }

  override fun setRepeatMode(
    repeatMode: Int
  ) {
    this.warnNotImplemented("setRepeatMode")
  }

  override fun getRepeatMode(): Int {
    this.warnNotImplemented("getRepeatMode")
    return Player.REPEAT_MODE_OFF
  }

  override fun setShuffleModeEnabled(
    shuffleModeEnabled: Boolean
  ) {
    this.warnNotImplemented("setShuffleModeEnabled")
  }

  override fun getShuffleModeEnabled(): Boolean {
    this.warnNotImplemented("getShuffleModeEnabled")
    return false
  }

  override fun isLoading(): Boolean {
    this.warnNotImplemented("isLoading")
    return false
  }

  override fun seekToDefaultPosition() {
    this.warnNotImplemented("seekToDefaultPosition")
  }

  override fun seekToDefaultPosition(
    mediaItemIndex: Int
  ) {
    this.warnNotImplemented("seekToDefaultPosition")
  }

  override fun seekTo(positionMs: Long) {
    this.warnNotImplemented("seekTo")
  }

  override fun seekTo(
    mediaItemIndex: Int,
    positionMs: Long
  ) {
    this.warnNotImplemented("seekTo")
  }

  override fun getSeekBackIncrement(): Long {
    this.warnNotImplemented("getSeekBackIncrement")
    return 0
  }

  override fun seekBack() {
    this.warnNotImplemented("seekBack")
  }

  override fun getSeekForwardIncrement(): Long {
    this.warnNotImplemented("getSeekForwardIncrement")
    return 0
  }

  override fun seekForward() {
    this.warnNotImplemented("seekForward")
  }

  @Deprecated("Deprecated in Java")
  override fun hasPrevious(): Boolean {
    this.warnNotImplemented("hasPrevious")
    return false
  }

  @Deprecated("Deprecated in Java")
  override fun hasPreviousWindow(): Boolean {
    this.warnNotImplemented("hasPreviousWindow")
    return false
  }

  override fun hasPreviousMediaItem(): Boolean {
    this.warnNotImplemented("hasPreviousMediaItem")
    return false
  }

  @Deprecated("Deprecated in Java")
  override fun previous() {
    this.warnNotImplemented("previous")
  }

  @Deprecated("Deprecated in Java")
  override fun seekToPreviousWindow() {
    this.warnNotImplemented("seekToPreviousWindow")
  }

  override fun seekToPreviousMediaItem() {
    this.warnNotImplemented("seekToPreviousMediaItem")
  }

  override fun getMaxSeekToPreviousPosition(): Long {
    this.warnNotImplemented("getMaxSeekToPreviousPosition")
    return 0L
  }

  override fun seekToPrevious() {
    this.warnNotImplemented("seekToPrevious")
  }

  @Deprecated("Deprecated in Java")
  override fun hasNext(): Boolean {
    this.warnNotImplemented("hasNext")
    return false
  }

  @Deprecated("Deprecated in Java")
  override fun hasNextWindow(): Boolean {
    this.warnNotImplemented("hasNextWindow")
    return false
  }

  override fun hasNextMediaItem(): Boolean {
    this.warnNotImplemented("hasNextMediaItem")
    return false
  }

  @Deprecated("Deprecated in Java")
  override fun next() {
    this.warnNotImplemented("next")
  }

  @Deprecated("Deprecated in Java")
  override fun seekToNextWindow() {
    this.warnNotImplemented("seekToNextWindow")
  }

  override fun seekToNextMediaItem() {
    this.warnNotImplemented("seekToNextMediaItem")
  }

  override fun seekToNext() {
    this.warnNotImplemented("seekToNext")
  }

  override fun setPlaybackParameters(
    playbackParameters: PlaybackParameters
  ) {
    this.warnNotImplemented("setPlaybackParameters")
  }

  override fun setPlaybackSpeed(
    speed: Float
  ) {
    this.warnNotImplemented("setPlaybackSpeed")
  }

  override fun getPlaybackParameters(): PlaybackParameters {
    this.warnNotImplemented("getPlaybackParameters")
    return PlaybackParameters(1.0f, 1.0f)
  }

  override fun stop() {
    this.logger.debug("stop")
    PlayerModel.pause()
  }

  override fun release() {
    this.warnNotImplemented("release")
  }

  override fun getCurrentTracks(): Tracks {
    this.warnNotImplemented("getCurrentTracks")
    return Tracks.EMPTY
  }

  override fun getTrackSelectionParameters(): TrackSelectionParameters {
    this.warnNotImplemented("getTrackSelectionParameters")
    return TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
  }

  override fun setTrackSelectionParameters(
    parameters: TrackSelectionParameters
  ) {
    this.warnNotImplemented("setTrackSelectionParameters")
  }

  override fun getMediaMetadata(): MediaMetadata {
    this.warnNotImplemented("getMediaMetadata")
    return MediaMetadata.EMPTY
  }

  override fun getPlaylistMetadata(): MediaMetadata {
    this.warnNotImplemented("getPlaylistMetadata")
    return MediaMetadata.EMPTY
  }

  override fun setPlaylistMetadata(
    mediaMetadata: MediaMetadata
  ) {
    this.warnNotImplemented("setPlaylistMetadata")
  }

  override fun getCurrentManifest(): Any? {
    this.warnNotImplemented("getCurrentManifest")
    return null
  }

  override fun getCurrentTimeline(): Timeline {
    this.warnNotImplemented("getCurrentTimeline")
    return Timeline.EMPTY
  }

  override fun getCurrentPeriodIndex(): Int {
    this.warnNotImplemented("getCurrentPeriodIndex")
    return 0
  }

  @Deprecated("Deprecated in Java")
  override fun getCurrentWindowIndex(): Int {
    this.warnNotImplemented("getCurrentWindowIndex")
    return 0
  }

  override fun getCurrentMediaItemIndex(): Int {
    this.warnNotImplemented("getCurrentMediaItemIndex")
    return 0
  }

  @Deprecated("Deprecated in Java")
  override fun getNextWindowIndex(): Int {
    this.warnNotImplemented("getNextWindowIndex")
    return 0
  }

  override fun getNextMediaItemIndex(): Int {
    this.warnNotImplemented("getNextMediaItemIndex")
    return 0
  }

  @Deprecated("Deprecated in Java")
  override fun getPreviousWindowIndex(): Int {
    this.warnNotImplemented("getPreviousWindowIndex")
    return 0
  }

  override fun getPreviousMediaItemIndex(): Int {
    this.warnNotImplemented("getPreviousMediaItemIndex")
    return 0
  }

  override fun getCurrentMediaItem(): MediaItem? {
    this.warnNotImplemented("getCurrentMediaItem")
    return null
  }

  override fun getMediaItemCount(): Int {
    this.warnNotImplemented("getMediaItemCount")
    return 0
  }

  override fun getMediaItemAt(
    index: Int
  ): MediaItem {
    this.warnNotImplemented("getMediaItemAt")
    return MediaItem.EMPTY
  }

  override fun getDuration(): Long {
    this.warnNotImplemented("getDuration")
    return 0L
  }

  override fun getCurrentPosition(): Long {
    this.warnNotImplemented("getCurrentPosition")
    return 0L
  }

  override fun getBufferedPosition(): Long {
    this.warnNotImplemented("getBufferedPosition")
    return 0L
  }

  override fun getBufferedPercentage(): Int {
    this.warnNotImplemented("getBufferedPercentage")
    return 0
  }

  override fun getTotalBufferedDuration(): Long {
    this.warnNotImplemented("getTotalBufferedDuration")
    return 0L
  }

  @Deprecated("Deprecated in Java")
  override fun isCurrentWindowDynamic(): Boolean {
    this.warnNotImplemented("isCurrentWindowDynamic")
    return false
  }

  override fun isCurrentMediaItemDynamic(): Boolean {
    this.warnNotImplemented("isCurrentMediaItemDynamic")
    return false
  }

  @Deprecated("Deprecated in Java")
  override fun isCurrentWindowLive(): Boolean {
    this.warnNotImplemented("isCurrentWindowLive")
    return false
  }

  override fun isCurrentMediaItemLive(): Boolean {
    this.warnNotImplemented("isCurrentMediaItemLive")
    return false
  }

  override fun getCurrentLiveOffset(): Long {
    this.warnNotImplemented("getCurrentLiveOffset")
    return 0L
  }

  @Deprecated("Deprecated in Java")
  override fun isCurrentWindowSeekable(): Boolean {
    this.warnNotImplemented("isCurrentWindowSeekable")
    return false
  }

  override fun isCurrentMediaItemSeekable(): Boolean {
    this.warnNotImplemented("isCurrentMediaItemSeekable")
    return false
  }

  override fun isPlayingAd(): Boolean {
    this.warnNotImplemented("isPlayingAd")
    return false
  }

  override fun getCurrentAdGroupIndex(): Int {
    this.warnNotImplemented("getCurrentAdGroupIndex")
    return 0
  }

  override fun getCurrentAdIndexInAdGroup(): Int {
    this.warnNotImplemented("getCurrentAdIndexInAdGroup")
    return 0
  }

  override fun getContentDuration(): Long {
    this.warnNotImplemented("getContentDuration")
    return 0L
  }

  override fun getContentPosition(): Long {
    this.warnNotImplemented("getContentPosition")
    return 0L
  }

  override fun getContentBufferedPosition(): Long {
    this.warnNotImplemented("getContentBufferedPosition")
    return 0L
  }

  override fun getAudioAttributes(): AudioAttributes {
    this.warnNotImplemented("getAudioAttributes")
    return AudioAttributes.DEFAULT
  }

  override fun setVolume(volume: Float) {
    this.warnNotImplemented("setVolume")
  }

  override fun getVolume(): Float {
    this.warnNotImplemented("getVolume")
    return 1.0f
  }

  override fun clearVideoSurface() {
    this.warnNotImplemented("clearVideoSurface")
  }

  override fun clearVideoSurface(
    surface: Surface?
  ) {
    this.warnNotImplemented("clearVideoSurface")
  }

  override fun setVideoSurface(
    surface: Surface?
  ) {
    this.warnNotImplemented("setVideoSurface")
  }

  override fun setVideoSurfaceHolder(
    surfaceHolder: SurfaceHolder?
  ) {
    this.warnNotImplemented("setVideoSurfaceHolder")
  }

  override fun clearVideoSurfaceHolder(
    surfaceHolder: SurfaceHolder?
  ) {
    this.warnNotImplemented("clearVideoSurfaceHolder")
  }

  override fun setVideoSurfaceView(
    surfaceView: SurfaceView?
  ) {
    this.warnNotImplemented("setVideoSurfaceView")
  }

  override fun clearVideoSurfaceView(
    surfaceView: SurfaceView?
  ) {
    this.warnNotImplemented("clearVideoSurfaceView")
  }

  override fun setVideoTextureView(
    textureView: TextureView?
  ) {
    this.warnNotImplemented("setVideoTextureView")
  }

  override fun clearVideoTextureView(
    textureView: TextureView?
  ) {
    this.warnNotImplemented("clearVideoTextureView")
  }

  override fun getVideoSize(): VideoSize {
    this.warnNotImplemented("getVideoSize")
    return VideoSize.UNKNOWN
  }

  override fun getSurfaceSize(): Size {
    this.warnNotImplemented("getSurfaceSize")
    return Size.UNKNOWN
  }

  override fun getCurrentCues(): CueGroup {
    this.warnNotImplemented("getCurrentCues")
    return CueGroup.EMPTY_TIME_ZERO
  }

  override fun getDeviceInfo(): DeviceInfo {
    this.warnNotImplemented("getDeviceInfo")
    return DeviceInfo.UNKNOWN
  }

  override fun getDeviceVolume(): Int {
    this.warnNotImplemented("getDeviceVolume")
    return 0
  }

  override fun isDeviceMuted(): Boolean {
    this.warnNotImplemented("isDeviceMuted")
    return false
  }

  @Deprecated("Deprecated in Java")
  override fun setDeviceVolume(volume: Int) {
    this.warnNotImplemented("setDeviceVolume")
  }

  override fun setDeviceVolume(volume: Int, flags: Int) {
    this.warnNotImplemented("setDeviceVolume")
  }

  @Deprecated("Deprecated in Java")
  override fun increaseDeviceVolume() {
    this.warnNotImplemented("increaseDeviceVolume")
  }

  override fun increaseDeviceVolume(flags: Int) {
    this.warnNotImplemented("increaseDeviceVolume")
  }

  @Deprecated("Deprecated in Java")
  override fun decreaseDeviceVolume() {
    this.warnNotImplemented("decreaseDeviceVolume")
  }

  override fun decreaseDeviceVolume(
    flags: Int
  ) {
    this.warnNotImplemented("decreaseDeviceVolume")
  }

  @Deprecated("Deprecated in Java")
  override fun setDeviceMuted(
    muted: Boolean
  ) {
    this.warnNotImplemented("setDeviceMuted")
  }

  override fun setDeviceMuted(
    muted: Boolean,
    flags: Int
  ) {
    this.warnNotImplemented("setDeviceMuted")
  }

  override fun setAudioAttributes(
    audioAttributes: AudioAttributes,
    handleAudioFocus: Boolean
  ) {
    this.warnNotImplemented("setAudioAttributes")
  }
}
