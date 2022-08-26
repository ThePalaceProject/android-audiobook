package org.librarysimplified.audiobook.views

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventError
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventManifestUpdated
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventChapterWaiting
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventCreateBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackBuffering
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackWaitingForAction
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerCancelled
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerFinished
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerRunning
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerStopped
import org.librarysimplified.audiobook.api.PlayerSleepTimerType
import org.librarysimplified.audiobook.api.PlayerSpineElementType
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.views.PlayerAccessibilityEvent.PlayerAccessibilityErrorOccurred
import org.librarysimplified.audiobook.views.PlayerAccessibilityEvent.PlayerAccessibilityIsBuffering
import org.librarysimplified.audiobook.views.PlayerAccessibilityEvent.PlayerAccessibilityIsWaitingForChapter
import org.slf4j.LoggerFactory
import rx.Subscription
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * A player fragment.
 *
 * New instances MUST be created with {@link #newInstance()} rather than calling the constructor
 * directly. The public constructor only exists because the Android API requires it.
 *
 * Activities hosting this fragment MUST implement the {@link org.librarysimplified.audiobook.views.PlayerFragmentListenerType}
 * interface. An exception will be raised if this is not the case.
 */

class PlayerFragment : Fragment() {

  companion object {

    const val parametersKey = "org.librarysimplified.audiobook.views.PlayerFragment.parameters"

    @JvmStatic
    fun newInstance(parameters: PlayerFragmentParameters): PlayerFragment {
      val args = Bundle()
      args.putSerializable(this.parametersKey, parameters)
      val fragment = PlayerFragment()
      fragment.arguments = args
      return fragment
    }
  }

  private lateinit var book: PlayerAudioBookType
  private lateinit var coverView: ImageView
  private lateinit var executor: ScheduledExecutorService
  private lateinit var listener: PlayerFragmentListenerType
  private lateinit var menuPlaybackRate: MenuItem
  private lateinit var menuPlaybackRateText: TextView
  private lateinit var menuSleep: MenuItem
  private lateinit var menuSleepEndOfChapter: ImageView
  private lateinit var menuSleepText: TextView
  private lateinit var menuTOC: MenuItem
  private lateinit var parameters: PlayerFragmentParameters
  private lateinit var playPauseButton: ImageView
  private lateinit var player: PlayerType
  private lateinit var playerAuthorView: TextView
  private lateinit var playerBookmark: ImageView
  private lateinit var playerCommands: ViewGroup
  private lateinit var playerDownloadingChapter: ProgressBar
  private lateinit var playerInfoModel: PlayerInfoModel
  private lateinit var playerPosition: SeekBar
  private lateinit var playerRemainingChapterTime: TextView
  private lateinit var playerService: PlayerService
  private lateinit var playerSkipBackwardButton: ImageView
  private lateinit var playerSkipForwardButton: ImageView
  private lateinit var playerSpineElement: TextView
  private lateinit var playerTimeCurrent: TextView
  private lateinit var playerTimeMaximum: TextView
  private lateinit var playerTitleView: TextView
  private lateinit var playerWaiting: TextView
  private lateinit var sleepTimer: PlayerSleepTimerType
  private lateinit var timeStrings: PlayerTimeStrings.SpokenTranslations
  private lateinit var toolbar: Toolbar
  private var clickedOnThumb: Boolean = false
  private var playerBufferingStillOngoing: Boolean = false
  private var playerBufferingTask: ScheduledFuture<*>? = null
  private var playerPositionDragging: Boolean = false

  private var currentPlaybackRate: PlayerPlaybackRate = PlayerPlaybackRate.NORMAL_TIME
  private var playerPositionCurrentSpine: PlayerSpineElementType? = null
  private var playerPositionCurrentOffset: Long = 0L
  private var playerEventSubscription: Subscription? = null
  private var playerSleepTimerEventSubscription: Subscription? = null

  private val log = LoggerFactory.getLogger(PlayerFragment::class.java)

  private val serviceConnection = object : ServiceConnection {

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      playerService = (binder as PlayerService.PlayerBinder).playerService
      initializePlayerInfo()
      playerService.updatePlayerInfo(playerInfoModel)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      // do nothing
    }
  }

  override fun onCreate(state: Bundle?) {
    this.log.debug("onCreate")

    super.onCreate(state)

    this.parameters =
      requireArguments().getSerializable(parametersKey)
        as PlayerFragmentParameters
    this.timeStrings =
      PlayerTimeStrings.SpokenTranslations.createFromResources(this.resources)
  }

  override fun onAttach(context: Context) {
    this.log.debug("onAttach")
    super.onAttach(context)

    if (context is PlayerFragmentListenerType) {
      this.listener = context
      this.player = this.listener.onPlayerWantsPlayer()
      this.book = this.listener.onPlayerTOCWantsBook()
      this.sleepTimer = this.listener.onPlayerWantsSleepTimer()
      this.executor = this.listener.onPlayerWantsScheduledExecutor()
    } else {
      throw ClassCastException(
        StringBuilder(64)
          .append("The activity hosting this fragment must implement one or more listener interfaces.\n")
          .append("  Activity: ")
          .append(context::class.java.canonicalName)
          .append('\n')
          .append("  Required interface: ")
          .append(PlayerFragmentListenerType::class.java.canonicalName)
          .append('\n')
          .toString()
      )
    }
  }

  private fun onPlayerSleepTimerEventsCompleted() {
    this.log.debug("onPlayerSleepTimerEventsCompleted")
  }

  private fun onPlayerSleepTimerError(error: Throwable) {
    this.log.error("onPlayerSleepTimerError: ", error)
  }

  private fun onPlayerSleepTimerEvent(event: PlayerSleepTimerEvent) {
    this.log.debug("onPlayerSleepTimerEvent: {}", event)

    return when (event) {
      PlayerSleepTimerStopped ->
        this.onPlayerSleepTimerEventStopped()
      is PlayerSleepTimerRunning ->
        this.onPlayerSleepTimerEventRunning(event)
      is PlayerSleepTimerCancelled ->
        this.onPlayerSleepTimerEventCancelled()
      PlayerSleepTimerFinished ->
        this.onPlayerSleepTimerEventFinished()
    }
  }

  private fun onPlayerSleepTimerEventFinished() {
    this.onPressedPause()

    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          this.menuSleepText.text = ""
          this.menuSleep.actionView.contentDescription = this.sleepTimerContentDescriptionSetUp()
          this.menuSleepText.visibility = INVISIBLE
          this.menuSleepEndOfChapter.visibility = INVISIBLE
        }
      }
    )
  }

  private fun onPlayerSleepTimerEventCancelled() {
    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          this.menuSleepText.text = ""
          this.menuSleep.actionView.contentDescription = this.sleepTimerContentDescriptionSetUp()
          this.menuSleepText.visibility = INVISIBLE
          this.menuSleepEndOfChapter.visibility = INVISIBLE
        }
      }
    )
  }

  private fun onPlayerSleepTimerEventRunning(event: PlayerSleepTimerRunning) {
    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          val remaining = event.remaining
          if (remaining != null) {
            this.menuSleep.actionView.contentDescription =
              this.sleepTimerContentDescriptionForTime(event.paused, remaining)
            this.menuSleepText.text =
              PlayerTimeStrings.minuteSecondTextFromDuration(remaining)
            this.menuSleepEndOfChapter.visibility = INVISIBLE
          } else {
            this.menuSleep.actionView.contentDescription =
              this.sleepTimerContentDescriptionEndOfChapter()
            this.menuSleepText.text = ""
            this.menuSleepEndOfChapter.visibility = VISIBLE
          }

          this.menuSleepText.visibility = VISIBLE
        }
      }
    )
  }

  private fun sleepTimerContentDescriptionEndOfChapter(): String {
    val builder = java.lang.StringBuilder(128)
    builder.append(this.resources.getString(R.string.audiobook_accessibility_menu_sleep_timer_icon))
    builder.append(". ")
    builder.append(this.resources.getString(R.string.audiobook_accessibility_sleep_timer_currently))
    builder.append(" ")
    builder.append(this.resources.getString(R.string.audiobook_accessibility_sleep_timer_description_end_of_chapter))
    return builder.toString()
  }

  private fun sleepTimerContentDescriptionForTime(
    paused: Boolean,
    remaining: Duration
  ): String {
    val builder = java.lang.StringBuilder(128)
    builder.append(this.resources.getString(R.string.audiobook_accessibility_menu_sleep_timer_icon))
    builder.append(". ")
    builder.append(this.resources.getString(R.string.audiobook_accessibility_sleep_timer_currently))
    builder.append(" ")
    builder.append(PlayerTimeStrings.minuteSecondSpokenFromDuration(this.timeStrings, remaining))
    if (paused) {
      builder.append(". ")
      builder.append(this.resources.getString(R.string.audiobook_accessibility_sleep_timer_is_paused))
    }
    return builder.toString()
  }

  private fun sleepTimerContentDescriptionSetUp(): String {
    val builder = java.lang.StringBuilder(128)
    builder.append(this.resources.getString(R.string.audiobook_accessibility_menu_sleep_timer_icon))
    builder.append(". ")
    builder.append(this.resources.getString(R.string.audiobook_accessibility_sleep_timer_currently))
    builder.append(" ")
    builder.append(this.resources.getString(R.string.audiobook_accessibility_sleep_timer_description_off))
    return builder.toString()
  }

  private fun playbackRateContentDescription(): String {
    val builder = java.lang.StringBuilder(128)
    builder.append(this.resources.getString(R.string.audiobook_accessibility_menu_playback_speed_icon))
    builder.append(". ")
    builder.append(this.resources.getString(R.string.audiobook_accessibility_playback_speed_currently))
    builder.append(" ")
    builder.append(
      PlayerPlaybackRateAdapter.contentDescriptionOfRate(
        this.resources,
        this.player.playbackRate
      )
    )
    return builder.toString()
  }

  private fun onPlayerSleepTimerEventStopped() {
    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          this.menuSleepText.text = ""
          this.menuSleepText.contentDescription = this.sleepTimerContentDescriptionSetUp()
          this.menuSleepText.visibility = INVISIBLE
          this.menuSleepEndOfChapter.visibility = INVISIBLE
        }
      }
    )
  }

  private fun onMenuTOCSelected(): Boolean {
    this.listener.onPlayerTOCShouldOpen()
    return true
  }

  private fun onMenuSleepSelected(): Boolean {
    this.listener.onPlayerSleepTimerShouldOpen()
    return true
  }

  private fun onMenuPlaybackRateSelected() {
    this.listener.onPlayerPlaybackRateShouldOpen()
  }

  private fun onToolbarNavigationSelected() {
    this.listener.onPlayerShouldBeClosed()
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    state: Bundle?
  ): View {
    this.log.debug("onCreateView")
    return inflater.inflate(R.layout.player_view, container, false)
  }

  override fun onDestroyView() {
    this.log.debug("onDestroyView")
    super.onDestroyView()
    this.playerEventSubscription?.unsubscribe()
    this.playerSleepTimerEventSubscription?.unsubscribe()
    this.onPlayerBufferingStopped()
    requireContext().unbindService(serviceConnection)
  }

  private fun configureToolbarActions() {

    this.toolbar.inflateMenu(R.menu.player_menu)

    this.toolbar.setNavigationOnClickListener { this.onToolbarNavigationSelected() }

    this.menuPlaybackRate = this.toolbar.menu.findItem(R.id.player_menu_playback_rate)

    /*
     * If the user is using a non-AppCompat theme, the action view will be null.
     * If this happens, we need to inflate the same view that would have been
     * automatically placed there by the menu definition.
     */

    if (this.menuPlaybackRate.actionView == null) {
      this.log.warn("received a null action view, likely due to a non-appcompat theme; inflating a replacement view")

      val actionView =
        this.layoutInflater.inflate(R.layout.player_menu_playback_rate_text, null)
      this.menuPlaybackRate.actionView = actionView
      this.menuPlaybackRate.setOnMenuItemClickListener { this.onMenuPlaybackRateSelected(); true }
    }

    this.menuPlaybackRate.actionView.setOnClickListener { this.onMenuPlaybackRateSelected() }
    this.menuPlaybackRate.actionView.contentDescription =
      this.playbackRateContentDescription()
    this.menuPlaybackRateText =
      this.menuPlaybackRate.actionView.findViewById(R.id.player_menu_playback_rate_text)

    this.menuPlaybackRateText.text =
      PlayerPlaybackRateAdapter.textOfRate(this.player.playbackRate)

    /*
     * On API versions older than 23, playback rate changes will have no effect. There is no
     * point showing the menu.
     */

    if (Build.VERSION.SDK_INT < 23) {
      this.menuPlaybackRate.isVisible = false
    }

    this.menuSleep = this.toolbar.menu.findItem(R.id.player_menu_sleep)

    /*
     * If the user is using a non-AppCompat theme, the action view will be null.
     * If this happens, we need to inflate the same view that would have been
     * automatically placed there by the menu definition.
     */

    if (this.menuSleep.actionView == null) {
      this.log.warn("received a null action view, likely due to a non-appcompat theme; inflating a replacement view")

      val actionView =
        this.layoutInflater.inflate(R.layout.player_menu_sleep_text, null)
      this.menuSleep.actionView = actionView
      this.menuSleep.setOnMenuItemClickListener { this.onMenuSleepSelected() }
    }

    this.menuSleep.actionView.setOnClickListener { this.onMenuSleepSelected() }
    this.menuSleep.actionView.contentDescription = this.sleepTimerContentDescriptionSetUp()

    this.menuSleepText = this.menuSleep.actionView.findViewById(R.id.player_menu_sleep_text)
    this.menuSleepText.text = ""
    this.menuSleepText.visibility = INVISIBLE

    this.menuSleepEndOfChapter =
      this.menuSleep.actionView.findViewById(R.id.player_menu_sleep_end_of_chapter)
    this.menuSleepEndOfChapter.visibility = INVISIBLE

    this.menuTOC = this.toolbar.menu.findItem(R.id.player_menu_toc)
    this.menuTOC.setOnMenuItemClickListener { this.onMenuTOCSelected() }

    /*
     * Subscribe to player and timer events. We do the subscription here (as late as possible)
     * so that all of the views (including the options menu) have been created before the first
     * event is received.
     */

    this.playerEventSubscription =
      this.player.events.subscribe(
        { event -> this.onPlayerEvent(event) },
        { error -> this.onPlayerError(error) },
        { this.onPlayerEventsCompleted() }
      )

    this.playerSleepTimerEventSubscription =
      this.sleepTimer.status.subscribe(
        { event -> this.onPlayerSleepTimerEvent(event) },
        { error -> this.onPlayerSleepTimerError(error) },
        { this.onPlayerSleepTimerEventsCompleted() }
      )
  }

  override fun onViewCreated(view: View, state: Bundle?) {
    this.log.debug("onViewCreated")
    super.onViewCreated(view, state)

    this.toolbar = view.findViewById(R.id.audioBookToolbar)
    configureToolbarActions()

    this.coverView = view.findViewById(R.id.player_cover)!!

    this.playerBookmark = view.findViewById(R.id.player_bookmark)
    this.playerBookmark.alpha = 0.0f

    this.playerDownloadingChapter = view.findViewById(R.id.player_downloading_chapter)
    this.playerCommands = view.findViewById(R.id.player_commands)

    this.playerTitleView = view.findViewById(R.id.player_title)
    this.playerAuthorView = view.findViewById(R.id.player_author)

    this.playPauseButton = view.findViewById(R.id.player_play_button)!!
    this.playPauseButton.setOnClickListener { this.onPressedPlay() }

    this.playerSkipForwardButton = view.findViewById(R.id.player_jump_forwards)
    this.playerSkipForwardButton.setOnClickListener { this.player.skipForward() }
    this.playerSkipBackwardButton = view.findViewById(R.id.player_jump_backwards)
    this.playerSkipBackwardButton.setOnClickListener { this.player.skipBack() }

    this.playerWaiting = view.findViewById(R.id.player_waiting_buffering)
    this.playerWaiting.text = ""
    this.playerWaiting.contentDescription = null

    this.playerPosition = view.findViewById(R.id.player_progress)!!
    this.playerPosition.isEnabled = false
    this.playerPositionDragging = false

    this.playerPosition.setOnTouchListener { _, event -> handleTouchOnSeekbar(event) }

    this.playerTimeCurrent = view.findViewById(R.id.player_time)!!
    this.playerTimeMaximum = view.findViewById(R.id.player_time_maximum)!!
    this.playerRemainingChapterTime = view.findViewById(R.id.player_remaining_chapter_time)!!
    this.playerSpineElement = view.findViewById(R.id.player_spine_element)!!
    this.playerSpineElement.text = this.spineElementText(this.book.spine.first())

    this.listener.onPlayerWantsCoverImage(this.coverView)
    this.playerTitleView.text = this.listener.onPlayerWantsTitle()
    this.playerAuthorView.text = this.listener.onPlayerWantsAuthor()

    this.player.playbackRate = this.parameters.currentRate ?: PlayerPlaybackRate.NORMAL_TIME

    initializeService()
  }

  private fun handleTouchOnSeekbar(event: MotionEvent?): Boolean {
    when (event?.action) {
      MotionEvent.ACTION_DOWN -> {
        clickedOnThumb = wasSeekbarThumbClicked(event)
        if (!clickedOnThumb) {
          return true
        }
      }
      MotionEvent.ACTION_UP -> {
        if (clickedOnThumb && playerPositionDragging) {
          playerPositionDragging = false
          clickedOnThumb = false
          onProgressBarDraggingStopped()
        } else {
          playerPositionDragging = false
          clickedOnThumb = false
          return true
        }
      }
      MotionEvent.ACTION_MOVE -> {
        if (!clickedOnThumb) {
          return true
        }
        playerPositionDragging = true
      }
      MotionEvent.ACTION_CANCEL -> {
        playerPositionDragging = false
        clickedOnThumb = false
      }
    }

    return playerPosition.onTouchEvent(event)
  }

  private fun onProgressBarDraggingStopped() {
    this.log.debug("onProgressBarDraggingStopped")

    val spine = this.playerPositionCurrentSpine
    if (spine != null) {
      val target = spine.position.copy(
        offsetMilliseconds =
        TimeUnit.MILLISECONDS.convert(this.playerPosition.progress.toLong(), TimeUnit.SECONDS)
      )
      if (this.player.isPlaying) {
        this.player.playAtLocation(target)
      } else {
        this.player.movePlayheadToLocation(target, playAutomatically = true)
      }
    }
  }

  private fun wasSeekbarThumbClicked(event: MotionEvent): Boolean {
    return this.playerPosition.thumb.bounds.contains(event.x.toInt(), event.y.toInt())
  }

  private fun onPlayerEventsCompleted() {
    this.log.debug("onPlayerEventsCompleted")
  }

  private fun onPlayerError(error: Throwable) {
    this.log.debug("onPlayerError: ", error)
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    this.log.debug("onPlayerEvent: {}", event)

    return when (event) {
      is PlayerEventPlaybackStarted ->
        this.onPlayerEventPlaybackStarted(event)
      is PlayerEventPlaybackBuffering ->
        this.onPlayerEventPlaybackBuffering(event)
      is PlayerEventChapterWaiting ->
        this.onPlayerEventChapterWaiting(event)
      is PlayerEventPlaybackProgressUpdate ->
        this.onPlayerEventPlaybackProgressUpdate(event)
      is PlayerEventPlaybackWaitingForAction ->
        this.onPlayerEventPlaybackWaitingForAction(event)
      is PlayerEventChapterCompleted ->
        this.onPlayerEventChapterCompleted()
      is PlayerEventPlaybackPaused ->
        this.onPlayerEventPlaybackPaused(event)
      is PlayerEventPlaybackStopped ->
        this.onPlayerEventPlaybackStopped(event)
      is PlayerEventPlaybackRateChanged ->
        this.onPlayerEventPlaybackRateChanged(event)
      is PlayerEventError ->
        this.onPlayerEventError(event)
      PlayerEventManifestUpdated ->
        this.onPlayerEventManifestUpdated()
      is PlayerEventCreateBookmark ->
        this.onPlayerEventCreateBookmark(event)
    }
  }

  /*
   * Show a small icon to demonstrate that a remote bookmark was created.
   */

  private fun onPlayerEventCreateBookmark(event: PlayerEventCreateBookmark) {
    if (event.isLocalBookmark) {
      return
    }

    UIThread.runOnUIThread {
      safelyPerformOperations {
        this.playerBookmark.alpha = 0.5f
        this.playerBookmark.animation =
          AnimationUtils.loadAnimation(this.context, R.anim.zoom_fade)
      }
    }
  }

  private fun onPlayerEventError(event: PlayerEventError) {
    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          val text = this.getString(R.string.audiobook_player_error, event.errorCode)
          this.playerDownloadingChapter.visibility = GONE
          this.playerCommands.visibility = VISIBLE
          this.playerWaiting.text = text
          this.playerWaiting.contentDescription = null
          this.listener.onPlayerAccessibilityEvent(PlayerAccessibilityErrorOccurred(text))

          val element = event.spineElement
          if (element != null) {
            this.configureSpineElementText(element, isPlaying = false)
            this.onEventUpdateTimeRelatedUI(element, event.offsetMilliseconds)
          }
        }
      }
    )
  }

  private fun onPlayerEventChapterWaiting(event: PlayerEventChapterWaiting) {
    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          this.playerDownloadingChapter.visibility = VISIBLE
          this.playerCommands.visibility = GONE
          val text =
            this.getString(R.string.audiobook_player_waiting, event.spineElement.index + 1)
          this.playerWaiting.text = text
          this.playerWaiting.contentDescription = null
          this.listener.onPlayerAccessibilityEvent(PlayerAccessibilityIsWaitingForChapter(text))

          this.configureSpineElementText(event.spineElement, isPlaying = false)
          this.onEventUpdateTimeRelatedUI(event.spineElement, 0)
        }
      }
    )
  }

  private fun onPlayerEventPlaybackBuffering(event: PlayerEventPlaybackBuffering) {
    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          this.onPlayerBufferingStarted()
          this.configureSpineElementText(event.spineElement, isPlaying = false)
          this.onEventUpdateTimeRelatedUI(event.spineElement, event.offsetMilliseconds)
        }
      }
    )
  }

  private fun onPlayerEventChapterCompleted() {
    this.onPlayerBufferingStopped()

    /*
     * If the chapter is completed, and the sleep timer is running indefinitely, then
     * tell the sleep timer to complete.
     */

    val running = this.sleepTimer.isRunning
    if (running != null) {
      if (running.duration == null) {
        this.sleepTimer.finish()
      }
    }
  }

  private fun onPlayerEventPlaybackRateChanged(event: PlayerEventPlaybackRateChanged) {
    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          this.currentPlaybackRate = event.rate
          this.menuPlaybackRateText.text = PlayerPlaybackRateAdapter.textOfRate(event.rate)
          this.menuPlaybackRate.actionView.contentDescription =
            this.playbackRateContentDescription()
        }
      }
    )
  }

  private fun onPlayerEventPlaybackStopped(event: PlayerEventPlaybackStopped) {
    this.onPlayerBufferingStopped()

    UIThread.runOnUIThread(
      Runnable {
        this.playPauseButton.setImageResource(R.drawable.play_icon)
        this.playPauseButton.setOnClickListener { this.onPressedPlay() }
        this.playPauseButton.contentDescription =
          this.getString(R.string.audiobook_accessibility_play)
        this.configureSpineElementText(event.spineElement, isPlaying = false)
        this.onEventUpdateTimeRelatedUI(event.spineElement, event.offsetMilliseconds)
      }
    )
  }

  private fun onPlayerEventPlaybackWaitingForAction(event: PlayerEventPlaybackWaitingForAction) {
    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          this.playerDownloadingChapter.visibility = GONE
          this.playerCommands.visibility = VISIBLE
          this.playerWaiting.text = ""
          this.currentPlaybackRate = this.player.playbackRate
          this.playPauseButton.setImageResource(R.drawable.play_icon)
          this.playPauseButton.setOnClickListener { this.onPressedPlay() }
          this.playPauseButton.contentDescription =
            this.getString(R.string.audiobook_accessibility_play)
          this.configureSpineElementText(event.spineElement, isPlaying = false)
          this.onEventUpdateTimeRelatedUI(event.spineElement, event.offsetMilliseconds)
        }
      }
    )
  }

  private fun onPlayerEventPlaybackPaused(event: PlayerEventPlaybackPaused) {
    this.onPlayerBufferingStopped()

    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          this.currentPlaybackRate = this.player.playbackRate
          this.playPauseButton.setImageResource(R.drawable.play_icon)
          this.playPauseButton.setOnClickListener { this.onPressedPlay() }
          this.playPauseButton.contentDescription =
            this.getString(R.string.audiobook_accessibility_play)
          this.configureSpineElementText(event.spineElement, isPlaying = false)
          this.onEventUpdateTimeRelatedUI(event.spineElement, event.offsetMilliseconds)
        }
      }
    )
  }

  private fun onPressedPlay() {
    this.player.play()
    this.sleepTimer.unpause()
    this.playerInfoModel = this.playerInfoModel.copy(
      isPlaying = true
    )
    this.playerService.updatePlayerInfo(this.playerInfoModel)
  }

  private fun onPressedPause() {
    this.player.pause()
    this.sleepTimer.pause()
    this.playerInfoModel = this.playerInfoModel.copy(
      isPlaying = false
    )
    this.playerService.updatePlayerInfo(this.playerInfoModel)
  }

  private fun safelyPerformOperations(operations: () -> Unit) {

    // if the fragment is not attached anymore, we can't perform the operations. This may occur due
    //to a racing condition
    if (!this.isAdded) {
      return
    }

    operations()
  }

  private fun onPlayerEventPlaybackProgressUpdate(event: PlayerEventPlaybackProgressUpdate) {
    this.onPlayerBufferingStopped()
    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          this.playerDownloadingChapter.visibility = GONE
          this.playerCommands.visibility = VISIBLE
          this.playPauseButton.setImageResource(R.drawable.pause_icon)
          this.playPauseButton.setOnClickListener { this.onPressedPause() }
          this.playPauseButton.contentDescription =
            this.getString(R.string.audiobook_accessibility_pause)
          this.playerWaiting.text = ""
          this.playerWaiting.contentDescription = null
          this.onEventUpdateTimeRelatedUI(event.spineElement, event.offsetMilliseconds)
        }
      }
    )
  }

  private fun onPlayerEventPlaybackStarted(event: PlayerEventPlaybackStarted) {
    this.onPlayerBufferingStopped()

    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          this.playerDownloadingChapter.visibility = GONE
          this.playerCommands.visibility = VISIBLE
          this.player.playbackRate = this.currentPlaybackRate
          this.playPauseButton.setImageResource(R.drawable.pause_icon)
          this.playPauseButton.setOnClickListener { this.onPressedPause() }
          this.playPauseButton.contentDescription =
            this.getString(R.string.audiobook_accessibility_pause)
          this.configureSpineElementText(event.spineElement, isPlaying = true)
          this.playerPosition.isEnabled = true
          this.playerWaiting.text = ""
          this.onEventUpdateTimeRelatedUI(event.spineElement, event.offsetMilliseconds)
        }
      }
    )
  }

  private fun onPlayerEventManifestUpdated() {
  }

  private fun onEventUpdateTimeRelatedUI(
    spineElement: PlayerSpineElementType,
    offsetMilliseconds: Long
  ) {

    this.playerPosition.max =
      spineElement.duration?.standardSeconds?.toInt() ?: Int.MAX_VALUE

    this.playerPosition.isEnabled = true

    this.playerPositionCurrentSpine = spineElement
    this.playerPositionCurrentOffset = offsetMilliseconds

    if (!this.playerPositionDragging) {
      this.playerPosition.progress =
        TimeUnit.MILLISECONDS.toSeconds(offsetMilliseconds).toInt()
    }

    playerRemainingChapterTime.text =
      PlayerTimeStrings.hourMinuteTextFromRemainingTime(
        requireContext(),
        getCurrentAudiobookRemainingDuration(spineElement) - offsetMilliseconds
    )

    this.playerTimeMaximum.text =
      PlayerTimeStrings.hourMinuteSecondTextFromDurationOptional(spineElement.duration
        ?.minus(offsetMilliseconds))
    this.playerTimeMaximum.contentDescription =
      this.playerTimeRemainingSpokenOptional(offsetMilliseconds, spineElement.duration)

    this.playerTimeCurrent.text =
      PlayerTimeStrings.hourMinuteSecondTextFromMilliseconds(offsetMilliseconds)
    this.playerTimeCurrent.contentDescription =
      this.playerTimeCurrentSpoken(offsetMilliseconds)

    this.playerSpineElement.text = this.spineElementText(spineElement)

    // we just update the book chapter on the playerInfoModel if it's been initialized
    if (::playerInfoModel.isInitialized) {

      this.playerInfoModel = this.playerInfoModel.copy(
        bookChapterName = this.spineElementText(spineElement)
      )

      playerService.updatePlayerInfo(playerInfoModel)
    }
  }

  private fun playerTimeCurrentSpoken(offsetMilliseconds: Long): String {
    return this.getString(
      R.string.audiobook_accessibility_player_time_current,
      PlayerTimeStrings.hourMinuteSecondSpokenFromMilliseconds(this.timeStrings, offsetMilliseconds)
    )
  }

  private fun getCurrentAudiobookRemainingDuration(spineElement: PlayerSpineElementType): Long {
    val totalDuration = book.spine.sumOf { it.duration?.millis ?: 0L }
    val totalTimeElapsed = if (spineElement.index == 0) {
      0L
    } else {
      book.spine.subList(0, spineElement.index).sumOf { it.duration?.millis ?: 0L }
    }

    return totalDuration - totalTimeElapsed
  }

  private fun playerTimeRemainingSpoken(
    offsetMilliseconds: Long,
    duration: Duration
  ): String {

    val remaining =
      duration.minus(Duration.millis(offsetMilliseconds))

    return this.getString(
      R.string.audiobook_accessibility_player_time_remaining,
      PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(this.timeStrings, remaining)
    )
  }

  private fun playerTimeRemainingSpokenOptional(
    offsetMilliseconds: Long,
    duration: Duration?
  ): String {
    return duration?.let { time ->
      this.playerTimeRemainingSpoken(offsetMilliseconds, time)
    } ?: ""
  }

  private fun spineElementText(spineElement: PlayerSpineElementType): String {
    val title = spineElement.title ?: this.getString(
      R.string.audiobook_player_toc_track_n,
      spineElement.index + 1
    )

    return this.getString(
      R.string.audiobook_player_spine_element,
      title,
      spineElement.index + 1,
      spineElement.book.spine.size
    )
  }

  /**
   * Configure the chapter display (such as "Chapter 1 of 2") and update the accessibility content
   * description to give the same information.
   */

  private fun configureSpineElementText(element: PlayerSpineElementType, isPlaying: Boolean) {
    this.playerSpineElement.text = this.spineElementText(element)

    // we just update the book chapter on the playerInfoModel if it's been initialized
    if (::playerInfoModel.isInitialized) {

      this.playerInfoModel = this.playerInfoModel.copy(
        bookChapterName = this.spineElementText(element),
        isPlaying = isPlaying
      )

      playerService.updatePlayerInfo(playerInfoModel)
    }

    val accessibilityTitle = element.title ?: this.getString(
      R.string.audiobook_accessibility_toc_track_n,
      element.index + 1
    )

    this.playerSpineElement.contentDescription =
      this.getString(
        R.string.audiobook_accessibility_chapter_of,
        accessibilityTitle,
        element.index + 1,
        this.book.spine.size
      )
  }

  private fun initializePlayerInfo() {
    this.playerInfoModel = PlayerInfoModel(
      bookChapterName = this.spineElementText(this.book.spine.first()),
      bookCover = null,
      bookName = this.listener.onPlayerWantsTitle(),
      isPlaying = false,
      player = this.player,
      smallIcon = this.listener.onPlayerNotificationWantsSmallIcon()
    )

    this.listener.onPlayerNotificationWantsBookCover(this::onBookCoverLoaded)
  }

  /**
   * The player said that it has started buffering. Only display a message if it is still buffering
   * a few seconds from now.
   */

  private fun onPlayerBufferingStarted() {
    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          this.onPlayerBufferingStopTaskNow()
          this.playerBufferingStillOngoing = true
          this.playerBufferingTask =
            this.executor.schedule({ this.onPlayerBufferingCheckNow() }, 2L, TimeUnit.SECONDS)
        }
      }
    )
  }

  private fun onPlayerBufferingCheckNow() {
    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          if (this.playerBufferingStillOngoing) {
            val accessibleMessage =
              this.getString(R.string.audiobook_accessibility_player_buffering)
            this.playerWaiting.contentDescription = accessibleMessage
            this.playerWaiting.setText(R.string.audiobook_player_buffering)
            this.playerWaiting.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
            this.listener.onPlayerAccessibilityEvent(
              PlayerAccessibilityIsBuffering(
                accessibleMessage
              )
            )
          }
        }
      }
    )
  }

  private fun onPlayerBufferingStopped() {
    UIThread.runOnUIThread(
      Runnable {
        safelyPerformOperations {
          this.onPlayerBufferingStopTaskNow()
          this.playerBufferingStillOngoing = false
        }
      }
    )
  }

  private fun onPlayerBufferingStopTaskNow() {
    val future = this.playerBufferingTask
    if (future != null) {
      future.cancel(true)
      this.playerBufferingTask = null
    }
  }

  private fun initializeService() {
    val intent = Intent(requireContext(), PlayerService::class.java)
    requireContext().bindService(intent, serviceConnection, BIND_AUTO_CREATE)
  }

  private fun onBookCoverLoaded(bitmap: Bitmap) {
    this.playerInfoModel = this.playerInfoModel.copy(
      bookCover = bitmap
    )

    playerService.updatePlayerInfo(this.playerInfoModel)
  }
}
