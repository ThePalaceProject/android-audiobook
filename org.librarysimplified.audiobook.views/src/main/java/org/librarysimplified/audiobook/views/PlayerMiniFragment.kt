package org.librarysimplified.audiobook.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackBuffering
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPreparing
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerPauseReason
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewCoverImageChanged
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationCloseAll
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationExpand

/**
 * A compact, single-line player. It shows the cover, title, and transport controls for the
 * currently-open book. It does not manage its own visibility; the hosting activity decides
 * when to show and hide it. It emits [PlayerViewNavigationCloseAll] when its close button is
 * pressed and [PlayerViewNavigationExpand] when the bar is tapped to expand to the full player.
 */

class PlayerMiniFragment : PlayerBaseFragment() {
  private lateinit var author: MarqueeTextView
  private lateinit var close: LinearLayout
  private lateinit var cover: ImageView
  private lateinit var playPause: LinearLayout
  private lateinit var playPauseIcon: ImageView
  private lateinit var skipBack: LinearLayout
  private lateinit var skipForward: LinearLayout
  private lateinit var title: MarqueeTextView
  private var subscriptions: CompositeDisposable = CompositeDisposable()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val layout =
      inflater.inflate(R.layout.player_mini_view, container, false)

    this.author = layout.findViewById(R.id.player_mini_author)
    this.close = layout.findViewById(R.id.player_mini_close)
    this.cover = layout.findViewById(R.id.player_mini_cover)
    this.playPause = layout.findViewById(R.id.player_mini_play_pause)
    this.playPauseIcon = layout.findViewById(R.id.player_mini_play_pause_icon)
    this.skipBack = layout.findViewById(R.id.player_mini_skip_back)
    this.skipForward = layout.findViewById(R.id.player_mini_skip_forward)
    this.title = layout.findViewById(R.id.player_mini_title)

    this.cover.setImageBitmap(PlayerModel.coverImage?.cover)
    this.title.text = PlayerModel.bookTitle
    this.author.text = PlayerModel.bookAuthor
    this.setPlayPauseButtonAppropriately()

    this.close.setOnClickListener {
      PlayerModel.submitViewCommand(PlayerViewNavigationCloseAll)
    }

    this.skipBack.setOnClickListener {
      PlayerModel.skipBack()
    }

    this.skipForward.setOnClickListener {
      PlayerModel.skipForward()
    }

    // Tapping the bar, anywhere except the buttons, expands to the full player.
    layout.setOnClickListener {
      PlayerModel.submitViewCommand(PlayerViewNavigationExpand)
    }

    return layout
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(
      PlayerModel.playerEvents
        .subscribe(this::onPlayerEvent)
    )

    this.subscriptions.add(
      PlayerModel.viewCommands
        .subscribe(this::onPlayerViewCommand)
    )

    this.title.startScrolling()
    this.author.startScrolling()
  }

  override fun onStop() {
    this.subscriptions.dispose()
    this.title.stopScrolling()
    this.author.stopScrolling()
    super.onStop()
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    when (event) {
      is PlayerEventPlaybackStarted,
      is PlayerEventPlaybackBuffering,
      is PlayerEventPlaybackPreparing -> {
        this.setButtonToShowPause()
      }

      is PlayerEventPlaybackPaused,
      is PlayerEventPlaybackStopped -> {
        this.setButtonToShowPlay()
      }

      else -> {
        // Nothing to do
      }
    }
  }

  private fun onPlayerViewCommand(command: PlayerViewCommand) {
    when (command) {
      is PlayerViewCoverImageChanged -> {
        this.cover.setImageBitmap(PlayerModel.coverImage?.cover)
      }

      else -> {
        // Navigation and other commands are handled by the hosting activity.
        // Nothing to do.
      }
    }
  }

  private fun setPlayPauseButtonAppropriately() {
    if (PlayerModel.isPlaying) {
      this.setButtonToShowPause()
    } else {
      this.setButtonToShowPlay()
    }
  }

  private fun setButtonToShowPlay() {
    this.playPauseIcon.setImageResource(R.drawable.play)
    this.playPause.setOnClickListener {
      PlayerModel.play()
    }
    this.playPause.contentDescription =
      this.getString(R.string.audiobook_accessibility_play)
  }

  private fun setButtonToShowPause() {
    this.playPauseIcon.setImageResource(R.drawable.round_pause_24)
    this.playPause.setOnClickListener {
      PlayerModel.pause(PlayerPauseReason.PAUSE_REASON_USER_EXPLICITLY_PAUSED)
    }
    this.playPause.contentDescription =
      this.getString(R.string.audiobook_accessibility_pause)
  }
}
