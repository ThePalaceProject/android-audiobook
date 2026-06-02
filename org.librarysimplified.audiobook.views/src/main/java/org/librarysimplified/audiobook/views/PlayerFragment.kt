package org.librarysimplified.audiobook.views

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.widget.Toolbar
import io.reactivex.disposables.CompositeDisposable
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerDownloadProgress
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
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
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerWaitReason.NETWORK_SETTINGS_DO_NOT_PERMIT_DOWNLOADS_OR_STREAMING
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerWaitReason.NETWORK_UNAVAILABLE
import org.librarysimplified.audiobook.api.PlayerPauseReason
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPlaybackRate.Companion.RATE_0_5
import org.librarysimplified.audiobook.api.PlayerPlaybackRate.Companion.RATE_1
import org.librarysimplified.audiobook.api.PlayerPlaybackRate.Companion.RATE_1_25
import org.librarysimplified.audiobook.api.PlayerPlaybackRate.Companion.RATE_1_5
import org.librarysimplified.audiobook.api.PlayerPlaybackRate.Companion.RATE_2
import org.librarysimplified.audiobook.api.PlayerPlaybackRate.Companion.RATE_MAX
import org.librarysimplified.audiobook.api.PlayerPlaybackRate.Companion.RATE_MIN
import org.librarysimplified.audiobook.api.PlayerSleepTimer
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.EndOfChapter
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.Off
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.WithDuration
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerFinished
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerStatusChanged
import org.librarysimplified.audiobook.api.PlayerSleepTimerType.Status.Paused
import org.librarysimplified.audiobook.api.PlayerSleepTimerType.Status.Running
import org.librarysimplified.audiobook.api.PlayerSleepTimerType.Status.Stopped
import org.librarysimplified.audiobook.manifest.api.PlayerManifestPositionMetadata
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsAbsolute
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewCoverImageChanged
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewErrorsDownloadOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewLoginOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationCloseAll
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationPlaybackRateMenuOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationSleepMenuOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCClose
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCOpen
import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.math.min

class PlayerFragment : PlayerBaseFragment() {

  private lateinit var playerRateDrawerViews: Set<View>
  private val logger =
    LoggerFactory.getLogger(PlayerFragment::class.java)

  private lateinit var playerRate: ViewGroup
  private lateinit var playerRateMinus: View
  private lateinit var playerRatePlus: View
  private lateinit var playerRateText: TextView
  private lateinit var playerRate2p0: Button
  private lateinit var playerRate1p5: Button
  private lateinit var playerRate1p25: Button
  private lateinit var playerRate1p0: Button
  private lateinit var playerRate0p5: Button
  private lateinit var playerRateSeekBar: SeekBar
  private var playerRateSeekBarChangingProgrammatically = false
  private lateinit var bottomSheet: PlayerBottomSheet
  private lateinit var bottomSheetDarken: View
  private lateinit var playerStatusButton: Button
  private lateinit var playerPauseReason: TextView
  private lateinit var coverView: ImageView
  private lateinit var menuAddBookmark: MenuItem
  private lateinit var menuPlaybackRate: MenuItem
  private lateinit var menuSleep: MenuItem
  private lateinit var menuSleepEndOfChapter: ImageView
  private lateinit var menuTOC: MenuItem
  private lateinit var playPauseButton: ImageView
  private lateinit var playerBookAuthor: TextView
  private lateinit var playerBookTitle: TextView
  private lateinit var playerBookmark: ImageView
  private lateinit var playerBusy: ProgressBar
  private lateinit var playerChapterTitle: TextView
  private lateinit var playerCommands: ViewGroup
  private lateinit var playerDebugStatus: TextView
  private lateinit var playerDownloadMessage: TextView
  private lateinit var playerDownloadProgressTotal: ProgressBar
  private lateinit var playerPosition: SeekBar
  private lateinit var playerRemainingBookTime: TextView
  private lateinit var playerSkipBackwardButton: ImageView
  private lateinit var playerSkipForwardButton: ImageView
  private lateinit var playerStatusArea: View
  private lateinit var playerStatusText: TextView
  private lateinit var playerStatusIcon: ImageView
  private lateinit var playerTimeCurrent: TextView
  private lateinit var playerTimeRemaining: TextView
  private lateinit var timeStrings: PlayerTimeStrings.SpokenTranslations
  private lateinit var toolbar: Toolbar

  private var subscriptions: CompositeDisposable = CompositeDisposable()
  private var playerPositionDragging: Boolean = false
  private var menuPlaybackRateText: TextView? = null
  private var menuSleepText: TextView? = null
  private val bottomSheetDarkenOpacityMax = 1.0f

  /*
   * Unfortunately, at API level 24, we can't store the minimum player position in the player
   * position seek bar. We have to store it separately here and do the arithmetic ourselves to
   * translate seek bar positions to player seek positions. This is updated every time the
   * player publishes a playback event. This violates our rule of having stateless fragments,
   * but it should be harmless as playback events are very frequent; the window of opportunity
   * for the code to view stale state is tiny.
   */

  @Volatile
  private var playerPositionMin: PlayerMillisecondsAbsolute = PlayerMillisecondsAbsolute(0)

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    state: Bundle?
  ): View {
    val view =
      inflater.inflate(R.layout.player_view, container, false)

    /*
     * Inject all view references before doing anything else.
     */

    this.toolbar =
      view.findViewById(R.id.audioBookToolbar)
    this.coverView =
      view.findViewById(R.id.player_cover)!!
    this.playerBusy =
      view.findViewById(R.id.player_busy)
    this.playerCommands =
      view.findViewById(R.id.player_commands)
    this.playerBookTitle =
      view.findViewById(R.id.playerBookTitle)
    this.playerBookAuthor =
      view.findViewById(R.id.playerBookAuthor)
    this.playPauseButton =
      view.findViewById(R.id.player_play_button)!!
    this.playerSkipForwardButton =
      view.findViewById(R.id.player_jump_forwards)
    this.playerSkipBackwardButton =
      view.findViewById(R.id.player_jump_backwards)
    this.playerTimeCurrent =
      view.findViewById(R.id.player_time)!!
    this.playerTimeRemaining =
      view.findViewById(R.id.player_time_maximum)!!
    this.playerRemainingBookTime =
      view.findViewById(R.id.player_remaining_book_time)!!
    this.playerChapterTitle =
      view.findViewById(R.id.playerChapterTitle)!!
    this.playerPosition =
      view.findViewById(R.id.player_progress)!!
    this.playerDebugStatus =
      view.findViewById(R.id.playerDebugStatus)

    this.playerStatusArea =
      view.findViewById(R.id.playerStatusArea)
    this.playerStatusIcon =
      view.findViewById(R.id.playerStatusIcon)
    this.playerStatusText =
      view.findViewById(R.id.playerStatusText)
    this.playerStatusButton =
      view.findViewById(R.id.playerStatusButton)

    this.playerDownloadMessage =
      view.findViewById(R.id.playerDownloadMessage)
    this.playerDownloadProgressTotal =
      view.findViewById(R.id.playerDownloadProgressTotal)

    this.playerDownloadProgressTotal.isIndeterminate = false

    this.playerPauseReason =
      view.findViewById(R.id.player_pause_reason)

    this.toolbar.setNavigationContentDescription(R.string.audiobook_accessibility_navigation_back)
    this.toolbarConfigureAllActions()

    this.playerPosition.isEnabled = false
    this.playerPositionDragging = false

    this.playPauseButton.setOnClickListener {
      PlayerModel.playOrPauseAsAppropriate(PlayerPauseReason.PAUSE_REASON_USER_EXPLICITLY_PAUSED)
    }
    this.playerSkipForwardButton.setOnClickListener {
      PlayerModel.skipForward()
    }
    this.playerSkipBackwardButton.setOnClickListener {
      PlayerModel.skipBack()
    }

    this.playerPosition.setOnTouchListener { _, event -> this.handleTouchOnSeekbar(event) }

    this.bottomSheet =
      view.findViewById(R.id.playerBottomSheet)
    this.bottomSheetDarken =
      view.findViewById(R.id.playerBottomSheetDarken)

    this.playerRate =
      this.bottomSheet.findViewById(R.id.playerRate)
    this.playerRateSeekBar =
      this.bottomSheet.findViewById(R.id.playerRateSeekBar)
    this.playerRateText =
      this.bottomSheet.findViewById(R.id.playerRateText)
    this.playerRate0p5 =
      this.bottomSheet.findViewById(R.id.playerRate0p5)
    this.playerRate1p0 =
      this.bottomSheet.findViewById(R.id.playerRate1p0)
    this.playerRate1p25 =
      this.bottomSheet.findViewById(R.id.playerRate1p25)
    this.playerRate1p5 =
      this.bottomSheet.findViewById(R.id.playerRate1p5)
    this.playerRate2p0 =
      this.bottomSheet.findViewById(R.id.playerRate2p0)
    this.playerRatePlus =
      this.bottomSheet.findViewById(R.id.playerRateButtonPlus)
    this.playerRateMinus =
      this.bottomSheet.findViewById(R.id.playerRateButtonMinus)

    this.playerRate0p5.contentDescription =
      this.getString(R.string.audiobook_accessibility_playback_speed_set_to, RATE_0_5.formatted)
    this.playerRate1p0.contentDescription =
      this.getString(R.string.audiobook_accessibility_playback_speed_set_to, RATE_1.formatted)
    this.playerRate1p25.contentDescription =
      this.getString(R.string.audiobook_accessibility_playback_speed_set_to, RATE_1_25.formatted)
    this.playerRate1p5.contentDescription =
      this.getString(R.string.audiobook_accessibility_playback_speed_set_to, RATE_1_5.formatted)
    this.playerRate2p0.contentDescription =
      this.getString(R.string.audiobook_accessibility_playback_speed_set_to, RATE_2.formatted)

    this.playerRate0p5.setOnClickListener {
      PlayerModel.setPlaybackRate(RATE_0_5)
    }
    this.playerRate1p0.setOnClickListener {
      PlayerModel.setPlaybackRate(RATE_1)
    }
    this.playerRate1p25.setOnClickListener {
      PlayerModel.setPlaybackRate(RATE_1_25)
    }
    this.playerRate1p5.setOnClickListener {
      PlayerModel.setPlaybackRate(RATE_1_5)
    }
    this.playerRate2p0.setOnClickListener {
      PlayerModel.setPlaybackRate(RATE_2)
    }
    this.playerRatePlus.setOnClickListener {
      PlayerModel.setPlaybackRate(
        PlayerPlaybackRate(
          min(RATE_MAX.speed, PlayerModel.playbackRate.speed + 0.1)
        )
      )
    }
    this.playerRateMinus.setOnClickListener {
      PlayerModel.setPlaybackRate(
        PlayerPlaybackRate(
          max(RATE_MIN.speed, PlayerModel.playbackRate.speed - 0.1)
        )
      )
    }

    this.playerRateDrawerViews =
      setOf<View>(
        this.playerRate,
        this.playerRate0p5,
        this.playerRate1p25,
        this.playerRate1p0,
        this.playerRate1p5,
        this.playerRate2p0,
        this.playerRateMinus,
        this.playerRatePlus,
        this.playerRateSeekBar,
        this.playerRateText,
      )

    this.playerDebugStatus.alpha = 0.0f
    this.playerStatusArea.alpha = 0.0f
    this.playerStatusButton.visibility = GONE
    this.coverView.setImageBitmap(PlayerModel.coverImage)
    return view
  }

  override fun onStart() {
    super.onStart()

    this.requireActivity().window.decorView.viewTreeObserver
      .addOnGlobalFocusChangeListener { oldFocus, newFocus ->
        this.onFocusChanged(oldFocus, newFocus)
      }

    this.timeStrings =
      PlayerTimeStrings.SpokenTranslations.createFromResources(this.resources)

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.playerEvents.subscribe { event ->
      this.onPlayerEvent(event)
    })
    this.subscriptions.add(PlayerSleepTimer.events.subscribe { event ->
      this.onSleepTimerEvent(event)
    })
    this.subscriptions.add(PlayerModel.viewCommands.subscribe { event ->
      this.onPlayerViewCommand(event)
    })
    this.subscriptions.add(PlayerModel.downloadEvents.subscribe { event ->
      this.onDownloadEvent()
    })
    this.subscriptions.add(PlayerObservableAuthorizationHandler.credentialsEvents.subscribe { event ->
      this.onOnCredentialsValid(event)
    })
    this.setPlayPauseButtonAppropriately()

    /*
     * Control the opacity of a full-screen darkening view based on the bottom sheet. When the
     * bottom sheet is fully expanded, the darkening view is mostly opaque. When the bottom sheet
     * is fully collapsed, the darkening view is transparent.
     */

    this.bottomSheetDarken.alpha = 0.0f
    this.bottomSheet.drawerCloseInstantly()
    this.bottomSheet.drawerSetHandleAccessibilityStrings(
      openHandle = R.string.audiobook_accessibility_playback_rate_open,
      closeHandle = R.string.audiobook_accessibility_playback_rate_close
    )

    this.playerRateViewsLock()

    this.bottomSheet.setOpenListener(object : PlayerBottomSheetType.SheetOpenListenerType {
      override fun onOpenChanged(state: Double) {
        val c = this@PlayerFragment
        c.bottomSheetDarken.alpha = (c.bottomSheetDarkenOpacityMax * state).toFloat()

        /*
         * If the drawer is fully open, make all the other views disabled. If the drawer is
         * fully closed, make all the views have their normal accessibility values. We use
         * a so-called "scrim" view - a translucent view that intercepts all click events -
         * to stop the user clicking on things in the background.
         */

        if (state >= 0.99) {
          c.bottomSheetDarken.setOnClickListener {
            c.bottomSheet.drawerClose()
          }
          c.bottomSheetDarken.isClickable = true
          c.playerRateViewsUnlock()
        } else if (state <= 0.01) {
          c.bottomSheetDarken.setOnClickListener(null)
          c.bottomSheetDarken.isClickable = false
          c.playerRateViewsLock()
        }
      }
    })

    this.playerRateSeekBar.progress = 100
    this.playerRateSeekBar.setOnSeekBarChangeListener(
      object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(
          p0: SeekBar?,
          p1: Int,
          p2: Boolean
        ) {
          if (this@PlayerFragment.playerRateSeekBarChangingProgrammatically) {
            return
          }
        }

        override fun onStartTrackingTouch(p0: SeekBar?) {
          if (this@PlayerFragment.playerRateSeekBarChangingProgrammatically) {
            return
          }
        }

        override fun onStopTrackingTouch(p0: SeekBar) {
          if (this@PlayerFragment.playerRateSeekBarChangingProgrammatically) {
            return
          }
          PlayerModel.setPlaybackRate(PlayerPlaybackRate(p0.progress.toDouble() / 100.0))
        }
      })

    this.onPlayerEventPlaybackRateChanged()
  }

  private fun onFocusChanged(
    oldFocus: View?,
    newFocus: View?
  ) {
    val oldClass: String?
    val oldName: String?
    val newClass: String?
    val newName: String?

    if (oldFocus != null) {
      oldClass = oldFocus.javaClass.simpleName
      oldName = oldFocus.resources.getResourceName(oldFocus.id)
    } else {
      oldClass = null
      oldName = null
    }

    if (newFocus != null) {
      newClass = newFocus.javaClass.simpleName
      newName = newFocus.resources.getResourceName(newFocus.id)
    } else {
      newClass = null
      newName = null
    }

    this.logger.debug("Focus: Now {}:{} (was: {}:{})", newName, newClass, oldName, oldClass)
    this.logger.debug(
      "Focus: {} {} {}",
      newFocus?.isClickable,
      newFocus?.isFocusable,
      newFocus?.isEnabled
    )
  }

  private fun playerRateViewsLock() {
    this.logger.debug("PlayerRateViews: lock")
    for (v in this.playerRateDrawerViews) {
      v.isEnabled = false
      v.isFocusable = false
      v.isClickable = false
    }
  }

  private fun playerRateViewsUnlock() {
    this.logger.debug("PlayerRateViews: unlock")
    for (v in this.playerRateDrawerViews) {
      v.isEnabled = true
      v.isFocusable = true
      v.isClickable = true
    }
  }

  /**
   * Set the play/pause button to the appropriate action, but don't change anything with regard
   * to whether the button is visible or not.
   */

  private fun setPlayPauseButtonAppropriately() {
    if (PlayerModel.isPlaying) {
      this.setButtonToShowPause()
    } else {
      this.setButtonToShowPlay()
    }
  }

  /**
   * Set the play/pause button to "pause", but don't change anything with regard to whether
   * the button is visible or not.
   */

  @UiThread
  private fun setButtonToShowPause() {
    this.playPauseButton.setImageResource(R.drawable.round_pause_24)
    this.playPauseButton.setOnClickListener { PlayerModel.pause(PlayerPauseReason.PAUSE_REASON_USER_EXPLICITLY_PAUSED) }
    this.playPauseButton.contentDescription =
      this.getString(R.string.audiobook_accessibility_pause)
  }

  /**
   * Set the play/pause button to "play", but don't change anything with regard to whether
   * the button is visible or not.
   */

  @UiThread
  private fun setButtonToShowPlay() {
    this.playPauseButton.setImageResource(R.drawable.baseline_play_arrow_24)
    this.playPauseButton.setOnClickListener { PlayerModel.play() }
    this.playPauseButton.contentDescription =
      this.getString(R.string.audiobook_accessibility_play)
  }

  @UiThread
  private fun onOnCredentialsValid(
    valid: Boolean
  ) {
    if (valid) {
      this.playerStatusArea.alpha = 0.0f
      this.playerStatusButton.visibility = GONE
    } else {
      this.playerStatusArea.alpha = 1.0f
      this.playerStatusText.text = this.resources.getString(R.string.audiobook_player_login_expired)
      this.playerStatusButton.visibility = VISIBLE
      this.playerStatusButton.setOnClickListener {
        PlayerModel.submitViewCommand(PlayerViewLoginOpen)
      }
    }
  }

  @UiThread
  private fun onDownloadEvent() {
    /*
     * First, search for a task that is downloading with an available progress value. This
     * is the task that's going to be most interesting in terms of what's going on in the
     * background.
     */

    this.playerDownloadMessage.text = ""
    this.playerDownloadProgressTotal.visibility = INVISIBLE

    val progress: PlayerDownloadProgress? =
      PlayerModel.findDownloadingProgressIfAny()

    if (progress != null) {
      this.playerDownloadMessage.text =
        this.resources.getString(R.string.audiobook_player_downloading_minimal)
      this.playerDownloadProgressTotal.visibility = VISIBLE
      this.playerDownloadProgressTotal.progress = PlayerModel.downloadProgress().asPercent()
      return
    }

    /*
     * If none of the tasks were downloading with an available progress value, then try
     * to find a task that's downloading but that _doesn't_ have an available progress
     * value. This indicates that the task is running, but is currently sitting waiting
     * for the server to respond.
     */

    val status: PlayerDownloadTaskStatus.Downloading? =
      PlayerModel.findDownloadingStatusIfAny()

    if (status != null) {
      this.playerDownloadMessage.text =
        this.resources.getString(R.string.audiobook_player_downloading_minimal)
      this.playerDownloadProgressTotal.visibility = VISIBLE
      this.playerDownloadProgressTotal.progress = PlayerModel.downloadProgress().asPercent()
      return
    }
  }

  @UiThread
  private fun onPlayerViewCommand(event: PlayerViewCommand) {
    return when (event) {
      PlayerViewCoverImageChanged -> {
        this.coverView.setImageBitmap(PlayerModel.coverImage)
      }

      PlayerViewNavigationPlaybackRateMenuOpen -> {
        this.bottomSheet.drawerOpen()
      }

      PlayerViewLoginOpen,
      PlayerViewErrorsDownloadOpen,
      PlayerViewNavigationCloseAll,
      PlayerViewNavigationSleepMenuOpen,
      PlayerViewNavigationTOCClose,
      PlayerViewNavigationTOCOpen -> {
        // Nothing to do
      }
    }
  }

  @UiThread
  private fun onSleepTimerEvent(
    event: PlayerSleepTimerEvent
  ) {
    return when (event) {
      PlayerSleepTimerFinished -> {
        // Nothing to do
      }

      is PlayerSleepTimerStatusChanged -> {
        this.onPlayerSleepTimerStatusChanged(event)
      }
    }
  }

  @UiThread
  private fun onPlayerSleepTimerStatusChanged(
    event: PlayerSleepTimerStatusChanged
  ) {
    return when (val s = event.newStatus) {
      is Paused -> this.onPlayerSleepTimerStatusPaused(s)
      is Running -> this.onPlayerSleepTimerStatusRunning(s)
      is Stopped -> this.onPlayerSleepTimerStatusStopped()
    }
  }

  @UiThread
  private fun onPlayerSleepTimerStatusStopped() {
    this.menuSleep.actionView?.contentDescription = this.sleepTimerContentDescriptionSetUp()
    this.menuSleepText?.text = ""
    this.menuSleepEndOfChapter.visibility = INVISIBLE
  }

  @UiThread
  private fun onPlayerSleepTimerStatusPaused(
    status: Paused
  ) {
    return when (val c = status.configuration) {
      EndOfChapter -> {
        this.menuSleep.actionView?.contentDescription =
          this.sleepTimerContentDescriptionEndOfChapter()
        this.menuSleepText?.text = ""
        this.menuSleepEndOfChapter.visibility = VISIBLE
      }

      Off -> {
        this.menuSleep.actionView?.contentDescription = this.sleepTimerContentDescriptionSetUp()
        this.menuSleepText?.text = ""
        this.menuSleepEndOfChapter.visibility = INVISIBLE
      }

      is WithDuration -> {
        this.menuSleep.actionView?.contentDescription =
          this.sleepTimerContentDescriptionForTime(paused = false, c.duration)
        this.menuSleepText?.text =
          PlayerTimeStrings.durationText(c.duration)
        this.menuSleepEndOfChapter.visibility = INVISIBLE
      }
    }
  }

  @UiThread
  private fun onPlayerSleepTimerStatusRunning(
    status: Running
  ) {
    return when (val c = status.configuration) {
      EndOfChapter -> {
        this.menuSleep.actionView?.contentDescription =
          this.sleepTimerContentDescriptionEndOfChapter()
        this.menuSleepText?.text = ""
        this.menuSleepEndOfChapter.visibility = VISIBLE
      }

      Off -> {
        this.menuSleep.actionView?.contentDescription = this.sleepTimerContentDescriptionSetUp()
        this.menuSleepText?.text = ""
        this.menuSleepEndOfChapter.visibility = INVISIBLE
      }

      is WithDuration -> {
        this.menuSleep.actionView?.contentDescription =
          this.sleepTimerContentDescriptionForTime(paused = true, c.duration)
        this.menuSleepText?.text =
          PlayerTimeStrings.durationText(c.duration)
        this.menuSleepEndOfChapter.visibility = INVISIBLE
      }
    }
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
    builder.append(PlayerTimeStrings.durationSpoken(this.timeStrings, remaining))
    if (paused) {
      builder.append(". ")
      builder.append(this.resources.getString(R.string.audiobook_accessibility_sleep_timer_is_paused))
    }
    return builder.toString()
  }

  @UiThread
  private fun onPlayerEvent(
    event: PlayerEvent
  ) {
    return when (event) {
      is PlayerAccessibilityEvent -> {
        // Nothing to do
      }

      is PlayerEventError -> {
        this.showError(event)
      }

      is PlayerEventManifestUpdated -> {
        // Nothing to do
      }

      is PlayerEventPlaybackRateChanged -> {
        this.onPlayerEventPlaybackRateChanged()
      }

      is PlayerEventChapterCompleted -> {
        // Nothing to do
      }

      is PlayerEventChapterWaiting -> {
        this.onPlayerEventPlaybackChapterWaiting(event)
      }

      is PlayerEventCreateBookmark -> {
        // Nothing to do
      }

      is PlayerEventPlaybackBuffering -> {
        this.onPlayerEventPlaybackBuffering(event)
      }

      is PlayerEventPlaybackPaused -> {
        this.onPlayerEventPlaybackPaused(event)
      }

      is PlayerEventPlaybackProgressUpdate -> {
        this.onPlayerEventPlaybackProgressUpdate(event)
      }

      is PlayerEventPlaybackStarted -> {
        this.onPlayerEventPlaybackStarted(event)
      }

      is PlayerEventPlaybackStopped -> {
        this.onPlayerEventPlaybackStopped(event)
      }

      is PlayerEventPlaybackWaitingForAction -> {
        // Nothing to do
      }

      is PlayerEventDeleteBookmark -> {
        // Nothing to do
      }

      is PlayerEventPlaybackPreparing -> {
        this.onPlayerEventPlaybackPreparing(event)
      }
    }
  }

  private fun showError(
    event: PlayerEventError
  ) {
    try {
      this.playerStatusIcon.setImageResource(R.drawable.player_status_error)
      this.publishStatusAreaMessage(
        this.resources.getString(
          R.string.audiobook_player_error,
          event.errorCodeName,
          event.errorCode
        )
      )
    } catch (_: Throwable) {
      // Nothing we can do about failures here.
    }
  }

  private fun publishStatusAreaMessage(
    message: String
  ) {
    this.playerStatusText.text = message
    this.playerStatusArea.alpha = 1.0f
    this.playerStatusButton.visibility = GONE
    this.playerStatusArea.animate()
      .alpha(0.0f)
      .setDuration(30000L)
      .start()
  }

  private fun publishPauseReason(
    reason: PlayerPauseReason
  ) {
    when (reason) {
      PlayerPauseReason.PAUSE_REASON_INITIALLY_PAUSED -> {
        this.playerPauseReason.text = ""
      }

      PlayerPauseReason.PAUSE_REASON_USER_EXPLICITLY_PAUSED -> {
        this.playerPauseReason.text = ""
      }

      PlayerPauseReason.PAUSE_REASON_BLUETOOTH_DEVICE_CHANGED -> {
        this.playerPauseReason.text =
          this.getString(R.string.audiobook_player_pause_reason_bluetooth)
      }

      PlayerPauseReason.PAUSE_REASON_AUDIO_FOCUS_LOST -> {
        this.playerPauseReason.text =
          this.getString(R.string.audiobook_player_pause_reason_focus)
      }

      PlayerPauseReason.PAUSE_REASON_SLEEP_TIMER -> {
        this.playerPauseReason.text =
          this.getString(R.string.audiobook_player_pause_sleep_timer)
      }
    }
  }

  private fun onPlayerEventPlaybackRateChanged() {
    try {
      val rate = PlayerModel.playbackRate
      val speed = rate.speed

      this.playerRateSeekBarChangingProgrammatically = true
      this.playerRateSeekBar.progress = (speed * 100.0).toInt()
      this.playerRateText.text = rate.formatted
      this.playerRateText.contentDescription = this.playbackRateIsCurrentlySetTo()
      this.playerRateSeekBar.contentDescription = this.playbackRateContentDescription()
      this.menuPlaybackRateText?.text = rate.formatted
    } finally {
      this.playerRateSeekBarChangingProgrammatically = false
    }
  }

  @UiThread
  private fun onPlayerEventPlaybackProgressUpdate(
    event: PlayerEventPlaybackProgressUpdate
  ) {
    this.onEventUpdateTimeRelatedUI(event.positionMetadata)
  }

  @UiThread
  private fun onPlayerEventPlaybackStarted(
    event: PlayerEventPlaybackStarted
  ) {
    this.playerDebugStatus.text = "Started"

    this.playerBusy.visibility = GONE
    this.playerCommands.visibility = VISIBLE

    this.playerPauseReason.text = ""
    this.playerStatusIcon.setImageBitmap(null)
    this.publishStatusAreaMessage("")

    this.playerPosition.isEnabled = true
    this.setButtonToShowPause()
    this.onEventUpdateTimeRelatedUI(event.positionMetadata)
  }

  @UiThread
  private fun onPlayerEventPlaybackChapterWaiting(
    event: PlayerEventChapterWaiting
  ) {
    this.playerDebugStatus.text = "Waiting for chapter to download…"

    this.playerStatusIcon.setImageResource(R.drawable.player_status_download)

    when (event.reason) {
      NETWORK_SETTINGS_DO_NOT_PERMIT_DOWNLOADS_OR_STREAMING -> {
        this.publishStatusAreaMessage(
          this.resources.getString(R.string.audiobook_player_waiting_network_denied)
        )
      }

      NETWORK_UNAVAILABLE -> {
        this.publishStatusAreaMessage(
          this.resources.getString(R.string.audiobook_player_waiting_network_unavailable)
        )
      }
    }

    this.playerBusy.visibility = VISIBLE
    this.playerCommands.visibility = INVISIBLE

    this.onEventUpdateTimeRelatedUI(event.positionMetadata)
  }

  @UiThread
  private fun onPlayerEventPlaybackBuffering(
    event: PlayerEventPlaybackBuffering
  ) {
    this.playerDebugStatus.text = "Buffering…"

    this.playerBusy.visibility = VISIBLE
    this.playerCommands.visibility = INVISIBLE

    this.setButtonToShowPause()
    this.onEventUpdateTimeRelatedUI(event.positionMetadata)
  }

  @UiThread
  private fun onPlayerEventPlaybackPreparing(
    event: PlayerEventPlaybackPreparing
  ) {
    this.playerDebugStatus.text = "Preparing…"

    this.playerBusy.visibility = VISIBLE
    this.playerCommands.visibility = INVISIBLE

    this.setButtonToShowPause()
    this.onEventUpdateTimeRelatedUI(event.positionMetadata)
  }

  @UiThread
  private fun onPlayerEventPlaybackStopped(
    event: PlayerEventPlaybackStopped
  ) {
    this.playerDebugStatus.text = "Stopped"

    this.playerBusy.visibility = GONE
    this.playerCommands.visibility = VISIBLE

    this.setButtonToShowPlay()
    this.onEventUpdateTimeRelatedUI(event.positionMetadata)
    this.publishPauseReason(event.reason)
  }

  @UiThread
  private fun onPlayerEventPlaybackPaused(
    event: PlayerEventPlaybackPaused
  ) {
    this.playerDebugStatus.text = "Paused"

    this.playerBusy.visibility = GONE
    this.playerCommands.visibility = VISIBLE

    this.setButtonToShowPlay()
    this.onEventUpdateTimeRelatedUI(event.positionMetadata)
    this.publishPauseReason(event.reason)
  }

  override fun onStop() {
    super.onStop()

    this.subscriptions.dispose()
  }

  private fun handleTouchOnSeekbar(event: MotionEvent?): Boolean {
    return when (event?.action) {
      MotionEvent.ACTION_DOWN -> {
        this.playerPositionDragging = true
        this.playerPosition.onTouchEvent(event)
      }

      MotionEvent.ACTION_UP -> {
        if (this.playerPositionDragging) {
          this.playerPositionDragging = false
          this.onReleasedPlayerPositionBar()
        }
        this.playerPosition.onTouchEvent(event)
      }

      MotionEvent.ACTION_CANCEL -> {
        this.playerPositionDragging = false
        this.playerPosition.onTouchEvent(event)
      }

      else -> {
        this.playerPosition.onTouchEvent(event)
      }
    }
  }

  private fun onReleasedPlayerPositionBar() {
    val tocItemOffsetMilliseconds =
      this.playerPosition.progress.toLong()
    val newOffset =
      PlayerMillisecondsAbsolute(this.playerPositionMin.value + tocItemOffsetMilliseconds)

    PlayerModel.movePlayheadToAbsoluteTime(newOffset)
  }

  private fun onEventUpdateTimeRelatedUI(
    positionMetadata: PlayerManifestPositionMetadata,
  ) {
    val lower =
      positionMetadata.tocItem.intervalAbsoluteMilliseconds.lower()
    val upperRelative =
      positionMetadata.tocItem.intervalAbsoluteMilliseconds.size().value.toInt()
    val progress =
      positionMetadata.tocItemPosition.millis.toInt()

    this.playerPositionMin = lower
    this.playerPosition.max = upperRelative
    this.playerPosition.isEnabled = true
    if (!this.playerPositionDragging) {
      this.playerPosition.progress = progress
    }

    this.playerChapterTitle.text = positionMetadata.tocItem.title
    this.playerBookTitle.text = PlayerModel.bookTitle
    this.playerBookAuthor.text = PlayerModel.bookAuthor

    this.playerRemainingBookTime.text =
      PlayerTimeStrings.remainingBookTime(
        this.requireContext(),
        positionMetadata.totalRemainingBookTime
      )

    this.playerTimeRemaining.text =
      PlayerTimeStrings.remainingTOCItemTime(positionMetadata.tocItemRemaining)

    this.playerTimeRemaining.contentDescription =
      PlayerTimeStrings.remainingTOCItemTimeSpoken(
        translations = this.timeStrings,
        time = positionMetadata.tocItemRemaining
      )

    this.playerTimeCurrent.text =
      PlayerTimeStrings.elapsedTOCItemTime(
        time = positionMetadata.tocItemPosition
      )

    this.playerTimeCurrent.contentDescription =
      PlayerTimeStrings.elapsedTOCItemTimeSpoken(
        translations = this.timeStrings,
        time = positionMetadata.tocItemPosition
      )
  }

  private fun toolbarConfigureAllActions() {
    this.toolbar.inflateMenu(R.menu.player_menu)
    this.toolbar.setNavigationOnClickListener {
      this.onToolbarNavigationSelected()
    }

    this.menuPlaybackRate =
      this.toolbar.menu.findItem(R.id.player_menu_playback_rate)

    /*
     * If the user is using a non-AppCompat theme, the action view will be null.
     * If this happens, we need to inflate the same view that would have been
     * automatically placed there by the menu definition.
     */

    if (this.menuPlaybackRate.actionView == null) {
      val actionView =
        this.layoutInflater.inflate(R.layout.player_menu_playback_rate_text, null)
      this.menuPlaybackRate.actionView = actionView
      this.menuPlaybackRate.setOnMenuItemClickListener {
        this.onMenuPlaybackRateSelected()
      }
    }

    this.menuPlaybackRate.actionView?.setOnClickListener {
      this.onMenuPlaybackRateSelected()
    }
    this.menuPlaybackRate.actionView?.contentDescription =
      this.playbackRateContentDescription()
    this.menuPlaybackRateText =
      this.menuPlaybackRate.actionView?.findViewById(R.id.player_menu_playback_rate_text)
    this.menuPlaybackRateText?.text = PlayerModel.playbackRate.formatted

    /*
     * On API versions older than 23, playback rate changes will have no effect. There is no
     * point showing the menu.
     */

    if (Build.VERSION.SDK_INT < 23) {
      this.menuPlaybackRate.isVisible = false
    }

    this.menuSleep =
      this.toolbar.menu.findItem(R.id.player_menu_sleep)

    /*
     * If the user is using a non-AppCompat theme, the action view will be null.
     * If this happens, we need to inflate the same view that would have been
     * automatically placed there by the menu definition.
     */

    if (this.menuSleep.actionView == null) {
      val actionView = this.layoutInflater.inflate(R.layout.player_menu_sleep_text, null)
      this.menuSleep.actionView = actionView
      this.menuSleep.setOnMenuItemClickListener { this.onMenuSleepSelected() }
    }

    this.menuSleep.actionView?.setOnClickListener { this.onMenuSleepSelected() }
    this.menuSleep.actionView?.contentDescription = this.sleepTimerContentDescriptionSetUp()

    this.menuSleepText = this.menuSleep.actionView?.findViewById(R.id.player_menu_sleep_text)
    this.menuSleepText?.text = ""

    this.menuSleepEndOfChapter =
      this.menuSleep.actionView!!.findViewById(R.id.player_menu_sleep_end_of_chapter)
    this.menuSleepEndOfChapter.visibility = INVISIBLE

    this.menuTOC = this.toolbar.menu.findItem(R.id.player_menu_toc)
    this.menuTOC.setOnMenuItemClickListener { this.onMenuTOCSelected() }

    this.menuAddBookmark = this.toolbar.menu.findItem(R.id.player_menu_add_bookmark)
    this.menuAddBookmark.setOnMenuItemClickListener { this.onMenuAddBookmarkSelected() }
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

  private fun playbackRateIsCurrentlySetTo(): String {
    val builder = java.lang.StringBuilder(128)
    builder.append(this.resources.getString(R.string.audiobook_accessibility_playback_speed_currently))
    builder.append(" ")
    builder.append(PlayerModel.playbackRate.formatted)
    return builder.toString()
  }

  private fun playbackRateContentDescription(): String {
    val builder = java.lang.StringBuilder(128)
    builder.append(this.resources.getString(R.string.audiobook_accessibility_menu_playback_speed_icon))
    builder.append(". ")
    builder.append(this.playbackRateIsCurrentlySetTo())
    return builder.toString()
  }

  private fun onMenuAddBookmarkSelected(): Boolean {
    PlayerModel.bookmarkCreate()
    return true
  }

  private fun onToolbarNavigationSelected(): Boolean {
    PlayerModel.submitViewCommand(PlayerViewNavigationCloseAll)
    return true
  }

  private fun onMenuPlaybackRateSelected(): Boolean {
    PlayerModel.submitViewCommand(PlayerViewNavigationPlaybackRateMenuOpen)
    return true
  }

  private fun onMenuTOCSelected(): Boolean {
    PlayerModel.submitViewCommand(PlayerViewNavigationTOCOpen)
    return true
  }

  private fun onMenuSleepSelected(): Boolean {
    PlayerModel.submitViewCommand(PlayerViewNavigationSleepMenuOpen)
    return true
  }
}
