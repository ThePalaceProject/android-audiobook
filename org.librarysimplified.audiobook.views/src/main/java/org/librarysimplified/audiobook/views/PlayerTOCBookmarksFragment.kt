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
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCClose

class PlayerTOCBookmarksFragment : Fragment() {

  private lateinit var adapter: PlayerTOCBookmarkAdapter
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

    this.adapter =
      PlayerTOCBookmarkAdapter(
        context = this.requireContext(),
        bookmarks = listOf(),
        onSelect = { position ->
          this.onBookmarkSelected(position)
        },
        onDelete = { index, bookmark ->
          this.onBookmarkDelete(index, bookmark)
        },
        onTitleLookup = { position ->
          this.onTitleLookup(position)
        }
      )
    this.list.adapter = this.adapter
    this.adapter.setBookmarks(PlayerBookmarkModel.bookmarks())

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerBookmarkModel.bookmarkEvents.subscribe(this::onBookmarksChanged))
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.dispose()
  }

  private fun onBookmarksChanged(
    bookmarks: List<PlayerBookmark>
  ) {
    this.adapter.setBookmarks(bookmarks)
  }

  private fun onBookmarkDelete(
    index: Int,
    bookmark: PlayerBookmark
  ) {
    PlayerModel.bookmarkDelete(bookmark)
  }

  private fun onBookmarkSelected(
    position: PlayerPosition
  ) {
    PlayerModel.movePlayheadTo(position)
    PlayerUIThread.runOnUIThreadDelayed({
      PlayerModel.submitViewCommand(PlayerViewNavigationTOCClose)
    }, 250L)
  }

  private fun onTitleLookup(
    position: PlayerPosition
  ): String {
    return PlayerModel.chapterTitleFor(position)
  }
}
