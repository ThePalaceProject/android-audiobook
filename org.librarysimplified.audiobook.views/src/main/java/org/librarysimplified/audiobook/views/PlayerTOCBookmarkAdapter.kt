package org.librarysimplified.audiobook.views

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
import org.librarysimplified.audiobook.api.PlayerUIThread
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
    PlayerUIThread.checkIsUIThread()

    val view =
      LayoutInflater.from(parent.context)
        .inflate(R.layout.player_toc_bookmark_item_view, parent, false)

    return this.BookmarkViewHolder(view)
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    PlayerUIThread.checkIsUIThread()

    (holder as? BookmarkViewHolder)?.bind(this.bookmarks[position])
  }

  fun setBookmarks(bookmarksList: List<PlayerBookmark>) {
    this.bookmarks = bookmarksList
  }

  inner class BookmarkViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
    private val bookmarkDate: TextView =
      this.itemView.findViewById(R.id.player_toc_bookmark_item_view_date)
    private val bookmarkOffset: TextView =
      this.itemView.findViewById(R.id.player_toc_bookmark_item_view_offset)
    private val bookmarkTitle: TextView =
      this.itemView.findViewById(R.id.player_toc_bookmark_item_view_title)
    private val bookmarkDelete: ImageView =
      this.itemView.findViewById(R.id.player_toc_bookmark_item_view_delete)
    private val bookmarkLoading: ProgressBar =
      this.itemView.findViewById(R.id.player_toc_bookmark_item_view_loading)

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
          this.itemView.context.getString(
            R.string.audiobook_accessibility_toc_bookmark_offset_is
          )
        )
        builder.append(" ")
        builder.append(
          PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(
            this@PlayerTOCBookmarkAdapter.timeStrings,
            offset
          )
        )
        builder.append(". ")
      }

      builder.append(
        this.itemView.context.getString(
          R.string.audiobook_accessibility_toc_bookmark_date_is
        )
      )
      builder.append(" ")
      builder.append(date)
      builder.append(". ")

      return builder.toString()
    }

    fun bind(bookmark: PlayerBookmark) {
      val offset =
        Duration(bookmark.offsetMilliseconds)
      val bookmarkDateStr =
        bookmark.metadata.creationTime.toString("MMMM dd, yyyy", Locale.ROOT)

      this.bookmarkTitle.text = bookmark.metadata.chapterTitle
      this.bookmarkOffset.text =
        this@PlayerTOCBookmarkAdapter.periodFormatter.print(offset.toPeriod())
      this.bookmarkDate.text = bookmarkDateStr

      this.bookmarkDelete.visibility = View.VISIBLE
      this.bookmarkLoading.visibility = View.GONE

      this.itemView.contentDescription =
        this.contentDescriptionOf(
          title = this.bookmarkTitle.text.toString(),
          offset = offset,
          date = bookmarkDateStr
        )

      this.bookmarkDelete.setOnClickListener {
        this@PlayerTOCBookmarkAdapter.onDelete(this.adapterPosition, bookmark)
      }

      this.itemView.setOnClickListener {
        this@PlayerTOCBookmarkAdapter.onSelect(bookmark.position)
      }
    }
  }
}
