package org.librarysimplified.audiobook.views

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.widget.Toolbar
import io.reactivex.disposables.CompositeDisposable
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventError
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventManifestUpdated
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterWaiting
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventCreateBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackBuffering
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackWaitingForAction
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCItem
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationPlaybackRateMenuOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationSleepMenuOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCOpen

class PlayerFragment2 : PlayerBaseFragment() {

  private var subscriptions: CompositeDisposable = CompositeDisposable()
  private var playerPositionDragging: Boolean = false
  private lateinit var coverView: ImageView
  private lateinit var menuAddBookmark: MenuItem
  private lateinit var menuPlaybackRate: MenuItem
  private lateinit var menuSleep: MenuItem
  private lateinit var menuSleepEndOfChapter: ImageView
  private lateinit var menuTOC: MenuItem
  private lateinit var playPauseButton: ImageView
  private lateinit var playerAuthorView: TextView
  private lateinit var playerBookmark: ImageView
  private lateinit var playerCommands: ViewGroup
  private lateinit var playerDownloadingChapter: ProgressBar
  private lateinit var playerInfoModel: PlayerInfoModel
  private lateinit var playerPosition: SeekBar
  private lateinit var playerRemainingBookTime: TextView
  private lateinit var playerSkipBackwardButton: ImageView
  private lateinit var playerSkipForwardButton: ImageView
  private lateinit var playerSpineElement: TextView
  private lateinit var playerTimeCurrent: TextView
  private lateinit var playerTimeMaximum: TextView
  private lateinit var playerTitleView: TextView
  private lateinit var playerWaiting: TextView
  private lateinit var timeStrings: PlayerTimeStrings.SpokenTranslations
  private lateinit var toolbar: Toolbar

  private var menuPlaybackRateText: TextView? = null
  private var menuSleepText: TextView? = null

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
    this.playerBookmark =
      view.findViewById(R.id.player_bookmark)
    this.playerDownloadingChapter =
      view.findViewById(R.id.player_downloading_chapter)
    this.playerCommands =
      view.findViewById(R.id.player_commands)
    this.playerTitleView =
      view.findViewById(R.id.player_title)
    this.playerAuthorView =
      view.findViewById(R.id.player_author)
    this.playPauseButton =
      view.findViewById(R.id.player_play_button)!!
    this.playerSkipForwardButton =
      view.findViewById(R.id.player_jump_forwards)
    this.playerSkipBackwardButton =
      view.findViewById(R.id.player_jump_backwards)
    this.playerWaiting =
      view.findViewById(R.id.player_waiting_buffering)
    this.playerTimeCurrent =
      view.findViewById(R.id.player_time)!!
    this.playerTimeMaximum =
      view.findViewById(R.id.player_time_maximum)!!
    this.playerRemainingBookTime =
      view.findViewById(R.id.player_remaining_book_time)!!
    this.playerSpineElement =
      view.findViewById(R.id.player_spine_element)!!
    this.playerPosition =
      view.findViewById(R.id.player_progress)!!

    this.toolbar.setNavigationContentDescription(R.string.audiobook_accessibility_navigation_back)
    this.toolbarConfigureAllActions()

    this.playerBookmark.alpha = 0.0f
    this.playerWaiting.text = ""
    this.playerWaiting.contentDescription = null
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
    return view
  }

  override fun onStart() {
    super.onStart()

    this.timeStrings =
      PlayerTimeStrings.SpokenTranslations.createFromResources(this.resources)

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.playerEvents.subscribe { event -> onPlayerEvent(event) })
  }

  @UiThread
  private fun onPlayerEvent(
    event: PlayerEvent
  ) {
    return when (event) {
      is PlayerEventError -> {
        // Nothing to do
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

      is PlayerEventPlaybackPaused ->
        this.onPlayerEventPlaybackPaused(event)

      is PlayerEventPlaybackProgressUpdate ->
        this.onPlayerEventPlaybackProgressUpdate(event)

      is PlayerEventPlaybackStarted ->
        this.onPlayerEventPlaybackStarted(event)

      is PlayerEventPlaybackStopped ->
        this.onPlayerEventPlaybackStopped(event)

      is PlayerEventPlaybackWaitingForAction -> {
        // Nothing to do
      }
    }
  }

  @UiThread
  private fun onPlayerEventPlaybackProgressUpdate(
    event: PlayerEventPlaybackProgressUpdate
  ) {
    this.onEventUpdateTimeRelatedUI(
      readingOrderItem = event.readingOrderItem,
      offsetMilliseconds = event.offsetMilliseconds,
      tocItem = event.tocItem,
      totalRemainingBookTime = event.totalRemainingBookTime
    )
  }

  @UiThread
  private fun onPlayerEventPlaybackStarted(event: PlayerEventPlaybackStarted) {
    this.playerDownloadingChapter.visibility = View.GONE
    this.playerCommands.visibility = View.VISIBLE
    this.playPauseButton.setImageResource(R.drawable.round_pause_24)
    this.playPauseButton.setOnClickListener { PlayerModel.pause() }
    this.playPauseButton.contentDescription =
      this.getString(R.string.audiobook_accessibility_pause)
    this.playerPosition.isEnabled = true
    this.playerWaiting.text = ""

    this.onEventUpdateTimeRelatedUI(
      readingOrderItem = event.readingOrderItem,
      offsetMilliseconds = event.offsetMilliseconds,
      tocItem = event.tocItem,
      totalRemainingBookTime = event.totalRemainingBookTime
    )
  }

  @UiThread
  private fun onPlayerEventPlaybackStopped(event: PlayerEventPlaybackStopped) {
    this.playPauseButton.setImageResource(R.drawable.baseline_play_arrow_24)
    this.playPauseButton.setOnClickListener { PlayerModel.play() }
    this.playPauseButton.contentDescription =
      this.getString(R.string.audiobook_accessibility_play)

    this.onEventUpdateTimeRelatedUI(
      readingOrderItem = event.readingOrderItem,
      offsetMilliseconds = event.offsetMilliseconds,
      tocItem = event.tocItem,
      totalRemainingBookTime = event.totalRemainingBookTime
    )
  }

  @UiThread
  private fun onPlayerEventPlaybackPaused(event: PlayerEventPlaybackPaused) {
    this.playPauseButton.setImageResource(R.drawable.baseline_play_arrow_24)
    this.playPauseButton.setOnClickListener { PlayerModel.play() }
    this.playPauseButton.contentDescription =
      this.getString(R.string.audiobook_accessibility_play)

    this.onEventUpdateTimeRelatedUI(
      readingOrderItem = event.readingOrderItem,
      offsetMilliseconds = event.offsetMilliseconds,
      tocItem = event.tocItem,
      totalRemainingBookTime = event.totalRemainingBookTime
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
    PlayerModel.seekTo(this.playerPosition.progress.toLong())
  }

  private fun onEventUpdateTimeRelatedUI(
    readingOrderItem: PlayerReadingOrderItemType,
    offsetMilliseconds: Long,
    tocItem: PlayerManifestTOCItem,
    totalRemainingBookTime: Duration
  ) {
    this.playerPosition.max =
      tocItem.durationMilliseconds.toInt()
    this.playerPosition.isEnabled = true

    if (!this.playerPositionDragging) {
      this.playerPosition.progress = offsetMilliseconds.toInt()
    }

    this.playerTitleView.text = tocItem.title

    this.playerRemainingBookTime.text =
      PlayerTimeStrings.hourMinuteTextFromRemainingTime(
        this.requireContext(),
        totalRemainingBookTime
      )

    this.playerTimeMaximum.text =
      PlayerTimeStrings.hourMinuteSecondTextFromDurationOptional(
        tocItem.duration.minus(Duration.millis(offsetMilliseconds))
      )

    this.playerTimeMaximum.contentDescription =
      this.playerTimeRemainingSpokenOptional(offsetMilliseconds, readingOrderItem.duration)
    this.playerTimeCurrent.text =
      PlayerTimeStrings.hourMinuteSecondTextFromMilliseconds(offsetMilliseconds)
    this.playerTimeCurrent.contentDescription =
      this.playerTimeCurrentSpoken(offsetMilliseconds)
  }

  private fun playerTimeCurrentSpoken(offsetMilliseconds: Long): String {
    return this.getString(
      R.string.audiobook_accessibility_player_time_current,
      PlayerTimeStrings.hourMinuteSecondSpokenFromMilliseconds(this.timeStrings, offsetMilliseconds)
    )
  }

  private fun playerTimeRemainingSpoken(
    offsetMilliseconds: Long,
    duration: Duration
  ): String {
    val remaining = duration.minus(Duration.millis(offsetMilliseconds))
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
      val actionView = this.layoutInflater.inflate(R.layout.player_menu_playback_rate_text, null)
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
    this.menuSleepEndOfChapter.visibility = View.INVISIBLE

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
    PlayerModel.closeBookOrDismissError()
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
