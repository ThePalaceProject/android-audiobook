package org.librarysimplified.audiobook.views.toc

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.views.PlayerFragmentListenerType
import org.librarysimplified.audiobook.views.R

/**
 * A table of content chapters fragment.
 *
 * New instances MUST be created with {@link #newInstance()} rather than calling the constructor
 * directly. The public constructor only exists because the Android API requires it.
 *
 * Activities hosting this fragment MUST implement the {@link org.librarysimplified.audiobook.views.PlayerFragmentListenerType}
 * interface. An exception will be raised if this is not the case.
 */

class PlayerTOCBookmarksFragment : Fragment() {

  companion object {

    fun newInstance(): PlayerTOCBookmarksFragment {
      return PlayerTOCBookmarksFragment()
    }
  }

  private lateinit var listener: PlayerFragmentListenerType
  private lateinit var adapter: PlayerTOCBookmarkAdapter
  private lateinit var player: PlayerType

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
    (list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)

    if (context is PlayerFragmentListenerType) {
      this.listener = context

      val bookmarks = this.listener.onPlayerTOCWantsBookmarks()

      this.adapter = PlayerTOCBookmarkAdapter(
        context = context,
        bookmarks = bookmarks,
        onSelect = { bookmarkPosition ->
          openBookmarkAndClose(bookmarkPosition)
        },
        onDelete = { index, bookmark ->
          AlertDialog.Builder(context)
            .setMessage(R.string.audiobook_player_toc_bookmarks_dialog_message_delete)
            .setPositiveButton(R.string.audiobook_player_toc_bookmarks_dialog_title_delete) { dialog, _ ->
              this.listener.onPlayerShouldDeleteBookmark(bookmark)
              updateBookmarks(index)
              dialog.dismiss()
            }
            .setNegativeButton(R.string.audiobook_player_options_cancel) { dialog, _ ->
              dialog.dismiss()
            }.show()
        }
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

  private fun updateBookmarks(index: Int) {
    val bookmarks = this.listener.onPlayerTOCWantsBookmarks()
    this.adapter.setBookmarks(bookmarks)
    this.adapter.notifyItemRemoved(index)
  }

  private fun openBookmarkAndClose(position: PlayerPosition) {
    this.player.playAtLocation(position)
    this.closeTOC()
  }

  private fun closeTOC() {
    this.listener.onPlayerTOCWantsClose()
  }
}
