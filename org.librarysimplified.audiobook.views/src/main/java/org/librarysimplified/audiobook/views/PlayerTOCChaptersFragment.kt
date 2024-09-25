package org.librarysimplified.audiobook.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerAccessibilityEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventDeleteBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventError
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventManifestUpdated
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCItem
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class PlayerTOCChaptersFragment : Fragment() {

  private val logger =
    LoggerFactory.getLogger(PlayerTOCChaptersFragment::class.java)

  private lateinit var adapter: PlayerTOCChapterAdapter
  private lateinit var list: RecyclerView
  private var subscriptions: CompositeDisposable = CompositeDisposable()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    state: Bundle?
  ): View {
    val view =
      inflater.inflate(R.layout.fragment_player_toc_chapter_view, container, false)

    this.list = view.findViewById(R.id.list)
    this.list.layoutManager = LinearLayoutManager(view.context)
    this.list.setHasFixedSize(true)

    /*
     * https://jira.nypl.org/browse/SIMPLY-1152
     *
     * By default, the RecyclerView will animate cells each time the underlying adapter is
     * notified that a cell has changed. This appears to be a completely broken "feature", because
     * all it actually does is screw up list rendering to the point that that cells bounce and
     * jiggle about more or less at random when the list scrolls. This gruesome line of code
     * turns the animation off.
     */

    (this.list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    return view
  }

  override fun onStart() {
    super.onStart()

    val book = PlayerModel.book()
    this.adapter =
      PlayerTOCChapterAdapter(
        context = this.requireContext(),
        book = book,
        onSelect = { item -> this.onTOCItemSelected(item) }
      )
    this.list.adapter = this.adapter

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.playerEvents.subscribe(this::onPlayerEvent))
    this.subscriptions.add(PlayerModel.downloadEvents.subscribe(this::onDownloadEvent))
  }

  private fun onDownloadEvent(status: PlayerReadingOrderItemDownloadStatus) {
    this.adapter.update(status)
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    when (event) {
      is PlayerEventWithPosition -> {
        this.adapter.setCurrentTOCItemIndex(event.positionMetadata.tocItem.index)
      }

      is PlayerEventDeleteBookmark,
      is PlayerEventError,
      PlayerEventManifestUpdated,
      is PlayerEventPlaybackRateChanged,
      is PlayerAccessibilityEvent,
      PlayerEventManifestUpdated -> {
        // Nothing to do.
      }
    }
  }

  private fun onTOCItemSelected(item: PlayerManifestTOCItem) {
    PlayerModel.movePlayheadToAbsoluteTime(item.intervalAbsoluteMilliseconds.lower)

    PlayerUIThread.runOnUIThreadDelayed({
      PlayerModel.submitViewCommand(PlayerViewCommand.PlayerViewNavigationTOCClose)
    }, 250L)

    PlayerUIThread.runOnUIThreadDelayed({
      PlayerModel.play()
    }, 1000L)

    /*
     * On some devices, selecting a chapter doesn't seem to start the player playing. Schedule
     * a task to log a warning if we detect that this has happened.
     */

    PlayerUIThread.runOnUIThreadDelayed({
      if (!PlayerModel.isPlaying) {
        MDC.put("Ticket", "PP-1703")
        try {
          this.logger.warn("Player appears not to be playing.")
        } finally {
          MDC.remove("Ticket")
        }
      }
    }, 2000L)
  }

  override fun onStop() {
    super.onStop()

    this.subscriptions.dispose()
  }
}
