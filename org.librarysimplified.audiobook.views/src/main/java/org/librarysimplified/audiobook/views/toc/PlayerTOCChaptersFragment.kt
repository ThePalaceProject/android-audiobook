package org.librarysimplified.audiobook.views.toc

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.disposables.Disposable
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadExpired
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadFailed
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloading
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.views.PlayerAccessibilityEvent.PlayerAccessibilityChapterSelected
import org.librarysimplified.audiobook.views.PlayerFragmentListenerType
import org.librarysimplified.audiobook.views.R
import org.slf4j.LoggerFactory

/**
 * A table of content chapters fragment.
 *
 * New instances MUST be created with {@link #newInstance()} rather than calling the constructor
 * directly. The public constructor only exists because the Android API requires it.
 *
 * Activities hosting this fragment MUST implement the {@link org.librarysimplified.audiobook.views.PlayerFragmentListenerType}
 * interface. An exception will be raised if this is not the case.
 */

class PlayerTOCChaptersFragment : Fragment(), PlayerTOCInnerFragment {

  companion object {

    @JvmStatic
    fun newInstance(): PlayerTOCChaptersFragment {
      return PlayerTOCChaptersFragment()
    }
  }

  private val log = LoggerFactory.getLogger(PlayerTOCChaptersFragment::class.java)

  private lateinit var adapter: PlayerTOCChapterAdapter
  private lateinit var book: PlayerAudioBookType
  private lateinit var listener: PlayerFragmentListenerType
  private lateinit var player: PlayerType

  private var bookSubscription: Disposable? = null
  private var playerSubscription: Disposable? = null

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    state: Bundle?
  ): View {
    return inflater.inflate(R.layout.fragment_player_toc_chapter_view, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val list = view.findViewById<RecyclerView>(R.id.list)
    list.layoutManager = LinearLayoutManager(view.context)
    list.setHasFixedSize(true)
    list.adapter = this.adapter

    /*
     * https://jira.nypl.org/browse/SIMPLY-1152
     *
     * By default, the RecyclerView will animate cells each time the underlying adapter is
     * notified that a cell has changed. This appears to be a completely broken "feature", because
     * all it actually does is screw up list rendering to the point that that cells bounce and
     * jiggle about more or less at random when the list scrolls. This gruesome line of code
     * turns the animation off.
     */

    (list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
  }

  override fun onDestroy() {
    super.onDestroy()

    this.bookSubscription?.dispose()
    this.playerSubscription?.dispose()
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)

    if (context is PlayerFragmentListenerType) {
      this.listener = context

      this.book = this.listener.onPlayerTOCWantsBook()
      this.player = this.listener.onPlayerWantsPlayer()

      this.adapter =
        PlayerTOCChapterAdapter(
          context = context,
          spineElements = this.book.readingOrder,
          downloadTasksById = this.book.downloadTasksByID
        ) { item -> this.onTOCItemSelected(item) }

      this.bookSubscription =
        this.book.readingOrderElementDownloadStatus.subscribe(
          { status -> this.onSpineElementStatusChanged(status) },
          { error -> this.onSpineElementStatusError(error) },
          { }
        )

      this.playerSubscription =
        this.player.events.subscribe(
          { event -> this.onPlayerEvent(event) },
          { error -> this.onPlayerError(error) },
          { }
        )
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

  override fun onMenuStopAllSelected() {
    this.log.debug("onMenuStopAllSelected")

    val dialog =
      MaterialAlertDialogBuilder(this.requireContext())
        .setCancelable(true)
        .setMessage(R.string.audiobook_player_toc_menu_stop_all_confirm)
        .setPositiveButton(
          R.string.audiobook_part_download_stop
        ) { _: DialogInterface, _: Int -> onMenuStopAllSelectedConfirmed() }
        .setNegativeButton(
          R.string.audiobook_part_download_continue
        ) { _: DialogInterface, _: Int -> }
        .create()
    dialog.show()
  }

  override fun onMenuRefreshAllSelected() {
    this.log.debug("onMenuRefreshAllSelected")
    this.book.wholeBookDownloadTask.fetch()
  }

  private fun onMenuStopAllSelectedConfirmed() {
    this.log.debug("onMenuStopAllSelectedConfirmed")
    this.book.wholeBookDownloadTask.cancel()
  }

  private fun onTOCItemSelected(item: PlayerReadingOrderItemType) {
    this.log.debug("onTOCItemSelected: ", item.index)

    try {
      this.listener.onPlayerAccessibilityEvent(
        PlayerAccessibilityChapterSelected(
          this.getString(R.string.audiobook_accessibility_toc_selected, item.index + 1)
        )
      )
    } catch (ex: Exception) {
      this.log.debug("ignored exception in event handler: ", ex)
    }

    return when (item.downloadStatus) {
      is PlayerReadingOrderItemNotDownloaded ->
        if (this.book.supportsStreaming) {
          this.playItemAndClose(item)
        } else {
        }

      is PlayerReadingOrderItemDownloading ->
        if (this.book.supportsStreaming) {
          this.playItemAndClose(item)
        } else {
        }

      is PlayerReadingOrderItemDownloaded ->
        this.playItemAndClose(item)

      is PlayerReadingOrderItemDownloadFailed ->
        if (this.book.supportsStreaming) {
          this.playItemAndClose(item)
        } else {
        }

      is PlayerReadingOrderItemDownloadExpired ->
        if (this.book.supportsStreaming) {
          this.playItemAndClose(item)
        } else {
        }
    }
  }

  private fun playItemAndClose(item: PlayerReadingOrderItemType) {
    this.player.movePlayheadToLocation(item.startingPosition)
    this.player.play()
    this.closeTOC()
  }

  private fun closeTOC() {
    this.listener.onPlayerTOCWantsClose()
  }

  private fun onPlayerError(error: Throwable) {
    this.log.error("onPlayerError: ", error)
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    return when (event) {
      is PlayerEvent.PlayerEventPlaybackRateChanged -> Unit
      is PlayerEvent.PlayerEventWithSpineElement ->
        this.onPlayerSpineElement(event.spineElement.index)
      is PlayerEvent.PlayerEventError -> Unit
      PlayerEvent.PlayerEventManifestUpdated -> Unit
    }
  }

  private fun onPlayerSpineElement(index: Int) {
    PlayerUIThread.runOnUIThread {
      this.adapter.setCurrentSpineElement(index)
    }
  }

  private fun onSpineElementStatusError(error: Throwable?) {
    this.log.error("onSpineElementStatusError: ", error)
  }

  private fun onSpineElementStatusChanged(status: PlayerReadingOrderItemDownloadStatus) {
    PlayerUIThread.runOnUIThread {
      val spineElement = status.readingOrderItem
      this.adapter.notifyItemChanged(spineElement.index)
      (parentFragment as? PlayerTOCMainFragment)?.menusConfigureVisibility()
    }
  }
}
