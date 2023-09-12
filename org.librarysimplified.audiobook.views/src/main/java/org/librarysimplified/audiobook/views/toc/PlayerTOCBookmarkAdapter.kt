package org.librarysimplified.audiobook.views.toc

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.joda.time.Duration
import org.joda.time.format.PeriodFormatter
import org.joda.time.format.PeriodFormatterBuilder
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.views.PlayerTimeStrings
import org.librarysimplified.audiobook.views.R
import org.librarysimplified.audiobook.views.UIThread
import java.util.Locale

/**
 * A Recycler view adapter used to display and control the bookmarks of the table of contents.
 */

class PlayerTOCBookmarkAdapter(
  private val context: Context,
  private var bookmarks: List<PlayerBookmark>,
  private val onSelect: (PlayerPosition) -> Unit,
  private val onDelete: (Int, PlayerBookmark) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  private val periodFormatter: PeriodFormatter =
    PeriodFormatterBuilder()
      .printZeroAlways()
      .minimumPrintedDigits(2)
      .appendHours()
      .appendLiteral(":")
      .appendMinutes()
      .appendLiteral(":")
      .appendSeconds()
      .toFormatter()

  private val timeStrings =
    PlayerTimeStrings.SpokenTranslations.createFromResources(this.context.resources)

  override fun getItemCount(): Int = this.bookmarks.size

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    UIThread.checkIsUIThread()

    val view =
      LayoutInflater.from(parent.context)
        .inflate(R.layout.player_toc_bookmark_item_view, parent, false)

    return BookmarkViewHolder(view)
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    UIThread.checkIsUIThread()

    (holder as? BookmarkViewHolder)?.bind(bookmarks[position])
  }

  fun setItemBeingDeleted(beingDeleted: Boolean, position: Int) {
    setBookmarks(
      bookmarks.mapIndexed { index, bookmark ->
        PlayerBookmark(
          date = bookmark.date,
          duration = bookmark.duration,
          isBeingDeleted = if (position == index) {
            beingDeleted
          } else {
            bookmark.isBeingDeleted
          },
          position = bookmark.position,
          uri = bookmark.uri
        )
      }
    )
    notifyItemChanged(position)
  }

  fun setBookmarks(bookmarksList: List<PlayerBookmark>) {
    bookmarks = bookmarksList
  }

  inner class BookmarkViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
    private val bookmarkDate: TextView =
      itemView.findViewById(R.id.player_toc_bookmark_item_view_date)
    private val bookmarkOffset: TextView =
      itemView.findViewById(R.id.player_toc_bookmark_item_view_offset)
    private val bookmarkTitle: TextView =
      itemView.findViewById(R.id.player_toc_bookmark_item_view_title)
    private val bookmarkDelete: ImageView =
      itemView.findViewById(R.id.player_toc_bookmark_item_view_delete)
    private val bookmarkLoading: ProgressBar =
      itemView.findViewById(R.id.player_toc_bookmark_item_view_loading)

    private fun contentDescriptionOf(
      title: String,
      offset: Duration?,
      date: String
    ): String {
      val builder = StringBuilder(128)

      builder.append(title)
      builder.append(". ")

      if (offset != null) {
        builder.append(
          itemView.context.getString(
            R.string.audiobook_accessibility_toc_bookmark_offset_is
          )
        )
        builder.append(" ")
        builder.append(
          PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(timeStrings, offset)
        )
        builder.append(". ")
      }

      builder.append(
        itemView.context.getString(
          R.string.audiobook_accessibility_toc_bookmark_date_is
        )
      )
      builder.append(" ")
      builder.append(date)
      builder.append(". ")

      return builder.toString()
    }

    fun bind(bookmark: PlayerBookmark) {
      val offset = Duration(bookmark.position.currentOffset)
      val bookmarkDateStr = bookmark.date.toString("MMMM dd, yyyy", Locale.ROOT)

      bookmarkTitle.text = bookmark.position.title.orEmpty()
      bookmarkOffset.text = periodFormatter.print(offset.toPeriod())
      bookmarkDate.text = bookmarkDateStr

      if (bookmark.isBeingDeleted) {
        bookmarkDelete.visibility = View.GONE
        bookmarkLoading.visibility = View.VISIBLE
      } else {
        bookmarkDelete.visibility = View.VISIBLE
        bookmarkLoading.visibility = View.GONE
      }

      itemView.contentDescription = contentDescriptionOf(
        title = bookmark.position.title.orEmpty(),
        offset = offset,
        date = bookmarkDateStr
      )

      bookmarkDelete.setOnClickListener {
        onDelete(adapterPosition, bookmark)
      }

      itemView.setOnClickListener {
        onSelect(bookmark.position)
      }
    }
  }
}
