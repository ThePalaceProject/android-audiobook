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
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.widget.Toolbar
import io.reactivex.disposables.CompositeDisposable
import org.joda.time.Duration
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
import org.librarysimplified.audiobook.api.PlayerPositionMetadata
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
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
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewCoverImageChanged
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationCloseAll
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationPlaybackRateMenuOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationSleepMenuOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCClose
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCOpen

class PlayerFragment : PlayerBaseFragment() {

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
  private lateinit var playerDownloadMessage: TextView
  private lateinit var playerDownloadProgress: ProgressBar
  private lateinit var playerPosition: SeekBar
  private lateinit var playerRemainingBookTime: TextView
  private lateinit var playerSkipBackwardButton: ImageView
  private lateinit var playerSkipForwardButton: ImageView
  private lateinit var playerStatus: TextView
  private lateinit var playerTimeCurrent: TextView
  private lateinit var playerTimeRemaining: TextView
  private lateinit var playerWaiting: TextView
  private lateinit var timeStrings: PlayerTimeStrings.SpokenTranslations
  private lateinit var toolbar: Toolbar

  private var subscriptions: CompositeDisposable = CompositeDisposable()
  private var playerPositionDragging: Boolean = false
  private var menuPlaybackRateText: TextView? = null
  private var menuSleepText: TextView? = null

  /*
   * Unfortunately, at API level 24, we can't store the minimum player position in the player
   * position seek bar. We have to store it separately here and do the arithmetic ourselves to
   * translate seek bar positions to player seek positions. This is updated every time the
   * player publishes a playback event. This violates our rule of having stateless fragments,
   * but it should be harmless as playback events are very frequent; the window of opportunity
   * for the code to view stale state is tiny.
   *
   * In practice, we store offset of the current TOC item from the start of the reading order
   * item that contains it. This allows us to set the maximum value of the slider to the duration
   * of the TOC item in milliseconds, and then use these values to calculate the reading order
   * item offset to pass to the player for seek operations.
   */

  @Volatile
  private var playerPositionMin: Long = 0

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
    this.playerWaiting =
      view.findViewById(R.id.playerMustBeDownloaded)
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
    this.playerStatus =
      view.findViewById(R.id.player_status)

    this.playerDownloadMessage =
      view.findViewById(R.id.playerDownloadMessage)
    this.playerDownloadProgress =
      view.findViewById(R.id.playerDownloadProgress)

    this.toolbar.setNavigationContentDescription(R.string.audiobook_accessibility_navigation_back)
    this.toolbarConfigureAllActions()

    this.playerPosition.isEnabled = false
    this.playerPositionDragging = false

    this.playPauseButton.setOnClickListener {
      PlayerModel.playOrPauseAsAppropriate()
    }
    this.playerSkipForwardButton.setOnClickListener {
      PlayerModel.skipForward()
    }
    this.playerSkipBackwardButton.setOnClickListener {
      PlayerModel.skipBack()
    }

    this.playerPosition.setOnTouchListener { _, event -> this.handleTouchOnSeekbar(event) }

    this.coverView.setImageBitmap(PlayerModel.coverImage)
    return view
  }

  override fun onStart() {
    super.onStart()

    this.timeStrings =
      PlayerTimeStrings.SpokenTranslations.createFromResources(this.resources)

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.playerEvents.subscribe { event -> this.onPlayerEvent(event) })
    this.subscriptions.add(PlayerSleepTimer.events.subscribe { event -> this.onSleepTimerEvent(event) })
    this.subscriptions.add(PlayerModel.viewCommands.subscribe { event -> this.onPlayerViewCommand(event) })
    this.subscriptions.add(PlayerModel.downloadEvents.subscribe { event -> this.onDownloadEvent() })

    if (PlayerModel.isPlaying) {
      this.setButtonToShowPause()
    } else {
      this.setButtonToShowPlay()
    }
  }

  @UiThread
  private fun setButtonToShowPause() {
    this.playPauseButton.setImageResource(R.drawable.round_pause_24)
    this.playPauseButton.setOnClickListener { PlayerModel.pause() }
    this.playPauseButton.contentDescription =
      this.getString(R.string.audiobook_accessibility_pause)
  }

  @UiThread
  private fun setButtonToShowPlay() {
    this.playPauseButton.setImageResource(R.drawable.baseline_play_arrow_24)
    this.playPauseButton.setOnClickListener { PlayerModel.play() }
    this.playPauseButton.contentDescription =
      this.getString(R.string.audiobook_accessibility_play)
  }

  @UiThread
  private fun onDownloadEvent() {
    val tasks = PlayerModel.book().downloadTasks

    /*
     * First, search for a task that is downloading with an available progress value. This
     * is the task that's going to be most interesting in terms of what's going on in the
     * background.
     */

    this.playerDownloadMessage.text = ""
    this.playerDownloadProgress.visibility = INVISIBLE

    for (task in tasks) {
      when (val st = task.status) {
        is PlayerDownloadTaskStatus.Downloading -> {
          val progress = st.progress
          if (progress != null) {
            this.playerDownloadMessage.text =
              this.resources.getString(R.string.audiobook_player_downloading, task.index + 1)
            this.playerDownloadProgress.visibility = VISIBLE
            this.playerDownloadProgress.isIndeterminate = false
            this.playerDownloadProgress.progress = progress.toInt()
            return
          }
        }
        PlayerDownloadTaskStatus.Failed,
        PlayerDownloadTaskStatus.IdleDownloaded,
        PlayerDownloadTaskStatus.IdleNotDownloaded -> {
          // Nothing important to say here.
        }
      }
    }

    /*
     * If none of the tasks were downloading with an available progress value, then try
     * to find a task that's downloading but that _doesn't_ have an available progress
     * value. This indicates that the task is running, but is currently sitting waiting
     * for the server to respond.
     */

    for (task in tasks) {
      when (val st = task.status) {
        is PlayerDownloadTaskStatus.Downloading -> {
          val progress = st.progress
          if (progress == null) {
            this.playerDownloadMessage.text =
              this.resources.getString(R.string.audiobook_player_downloading, task.index + 1)
            this.playerDownloadProgress.visibility = VISIBLE
            this.playerDownloadProgress.isIndeterminate = true
            return
          }
        }
        PlayerDownloadTaskStatus.Failed,
        PlayerDownloadTaskStatus.IdleDownloaded,
        PlayerDownloadTaskStatus.IdleNotDownloaded -> {
          // Nothing important to say here.
        }
      }
    }

    /*
     * If none of the tasks were downloading, then some of them might have failed, and it's
     * worth announcing this...
     */

    for (task in tasks) {
      when (task.status) {
        is PlayerDownloadTaskStatus.Downloading,
        PlayerDownloadTaskStatus.IdleDownloaded,
        PlayerDownloadTaskStatus.IdleNotDownloaded -> {
          // Nothing important to say here.
        }

        PlayerDownloadTaskStatus.Failed -> {
          this.playerDownloadMessage.text =
            this.resources.getString(R.string.audiobook_player_downloading_failed, task.index + 1)
          this.playerDownloadProgress.visibility = INVISIBLE
          this.playerDownloadProgress.isIndeterminate = false
          return
        }
      }
    }
  }

  @UiThread
  private fun onPlayerViewCommand(event: PlayerViewCommand) {
    return when (event) {
      PlayerViewCoverImageChanged -> {
        this.coverView.setImageBitmap(PlayerModel.coverImage)
      }
      PlayerViewNavigationCloseAll,
      PlayerViewNavigationPlaybackRateMenuOpen,
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
        // Nothing to do
      }

      PlayerEventManifestUpdated -> {
        // Nothing to do
      }

      is PlayerEventPlaybackRateChanged -> {
        this.onPlayerEventPlaybackRateChanged(event)
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

  private fun onPlayerEventPlaybackRateChanged(
    event: PlayerEventPlaybackRateChanged
  ) {
    this.menuPlaybackRateText?.text =
      PlayerPlaybackRateAdapter.textOfRate(PlayerModel.playbackRate)
  }

  @UiThread
  private fun onPlayerEventPlaybackProgressUpdate(
    event: PlayerEventPlaybackProgressUpdate
  ) {
    this.onEventUpdateTimeRelatedUI(
      readingOrderItem = event.readingOrderItem,
      readingOrderItemOffsetMilliseconds = event.offsetMilliseconds,
      positionMetadata = event.positionMetadata,
    )
  }

  @UiThread
  private fun onPlayerEventPlaybackStarted(
    event: PlayerEventPlaybackStarted
  ) {
    this.playerStatus.text = "Started"

    this.playerWaiting.visibility = INVISIBLE
    this.playerBusy.visibility = GONE
    this.playerCommands.visibility = VISIBLE

    this.setButtonToShowPause()

    this.playerPosition.isEnabled = true

    this.onEventUpdateTimeRelatedUI(
      readingOrderItem = event.readingOrderItem,
      readingOrderItemOffsetMilliseconds = event.offsetMilliseconds,
      positionMetadata = event.positionMetadata,
    )
  }

  @UiThread
  private fun onPlayerEventPlaybackChapterWaiting(
    event: PlayerEventChapterWaiting
  ) {
    this.playerStatus.text = "Waiting for chapter to download…"

    this.playerWaiting.visibility = VISIBLE
    this.playerBusy.visibility = VISIBLE
    this.playerCommands.visibility = INVISIBLE

    this.onEventUpdateTimeRelatedUI(
      readingOrderItem = event.readingOrderItem,
      readingOrderItemOffsetMilliseconds = 0,
      positionMetadata = event.positionMetadata,
    )
  }

  @UiThread
  private fun onPlayerEventPlaybackBuffering(
    event: PlayerEventPlaybackBuffering
  ) {
    this.playerStatus.text = "Buffering…"

    this.playerWaiting.visibility = INVISIBLE
    this.playerBusy.visibility = VISIBLE
    this.playerCommands.visibility = INVISIBLE

    this.onEventUpdateTimeRelatedUI(
      readingOrderItem = event.readingOrderItem,
      readingOrderItemOffsetMilliseconds = event.offsetMilliseconds,
      positionMetadata = event.positionMetadata,
    )
  }

  @UiThread
  private fun onPlayerEventPlaybackPreparing(
    event: PlayerEventPlaybackPreparing
  ) {
    this.playerStatus.text = "Preparing…"

    this.playerWaiting.visibility = INVISIBLE
    this.playerBusy.visibility = VISIBLE
    this.playerCommands.visibility = INVISIBLE

    this.onEventUpdateTimeRelatedUI(
      readingOrderItem = event.readingOrderItem,
      readingOrderItemOffsetMilliseconds = event.offsetMilliseconds,
      positionMetadata = event.positionMetadata,
    )
  }

  @UiThread
  private fun onPlayerEventPlaybackStopped(
    event: PlayerEventPlaybackStopped
  ) {
    this.playerStatus.text = "Stopped"

    this.playerWaiting.visibility = INVISIBLE
    this.playerBusy.visibility = GONE
    this.playerCommands.visibility = VISIBLE

    this.setButtonToShowPlay()

    this.onEventUpdateTimeRelatedUI(
      readingOrderItem = event.readingOrderItem,
      readingOrderItemOffsetMilliseconds = event.readingOrderItemOffsetMilliseconds,
      positionMetadata = event.positionMetadata,
    )
  }

  @UiThread
  private fun onPlayerEventPlaybackPaused(
    event: PlayerEventPlaybackPaused
  ) {
    this.playerStatus.text = "Paused"

    this.playerBusy.visibility = GONE
    this.playerCommands.visibility = VISIBLE

    this.setButtonToShowPlay()

    this.onEventUpdateTimeRelatedUI(
      readingOrderItem = event.readingOrderItem,
      readingOrderItemOffsetMilliseconds = event.readingOrderItemOffsetMilliseconds,
      positionMetadata = event.positionMetadata,
    )
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
    val readingOrderItemOffsetMilliseconds =
      this.playerPositionMin + tocItemOffsetMilliseconds

    PlayerModel.seekTo(readingOrderItemOffsetMilliseconds)
  }

  private fun onEventUpdateTimeRelatedUI(
    readingOrderItem: PlayerReadingOrderItemType,
    readingOrderItemOffsetMilliseconds: Long,
    positionMetadata: PlayerPositionMetadata,
  ) {
    /*
     * The number of milliseconds into the TOC item is the current reading order offset milliseconds
     * minus the offset of the TOC item.
     */

    val tocItemOffsetMilliseconds =
      Math.max(0, readingOrderItemOffsetMilliseconds - positionMetadata.tocItem.readingOrderOffsetMilliseconds)

    this.playerPositionMin =
      positionMetadata.tocItem.readingOrderOffsetMilliseconds
    this.playerPosition.max =
      positionMetadata.tocItem.durationMilliseconds.toInt()

    this.playerPosition.isEnabled = true

    if (!this.playerPositionDragging) {
      this.playerPosition.progress = tocItemOffsetMilliseconds.toInt()
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
      PlayerTimeStrings.remainingTOCItemTime(
        positionMetadata.tocItem.duration.minus(
          Duration.millis(tocItemOffsetMilliseconds)
        )
      )

    this.playerTimeRemaining.contentDescription =
      PlayerTimeStrings.remainingTOCItemTimeSpoken(
        translations = timeStrings,
        time = positionMetadata.tocItem.duration.minus(Duration.millis(tocItemOffsetMilliseconds))
      )

    this.playerTimeCurrent.text =
      PlayerTimeStrings.elapsedTOCItemTime(
        time = Duration.millis(tocItemOffsetMilliseconds)
      )

    this.playerTimeCurrent.contentDescription =
      PlayerTimeStrings.elapsedTOCItemTimeSpoken(
        translations = timeStrings,
        time = Duration.millis(tocItemOffsetMilliseconds)
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
    this.menuPlaybackRateText?.text =
      PlayerPlaybackRateAdapter.textOfRate(PlayerModel.playbackRate)

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

  private fun playbackRateContentDescription(): String {
    val builder = java.lang.StringBuilder(128)
    builder.append(this.resources.getString(R.string.audiobook_accessibility_menu_playback_speed_icon))
    builder.append(". ")
    builder.append(this.resources.getString(R.string.audiobook_accessibility_playback_speed_currently))
    builder.append(" ")
    builder.append(
      PlayerPlaybackRateAdapter.contentDescriptionOfRate(this.resources, PlayerModel.playbackRate)
    )
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
