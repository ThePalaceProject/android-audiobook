package org.librarysimplified.audiobook.views

import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.joda.time.Duration
import org.joda.time.format.PeriodFormatter
import org.joda.time.format.PeriodFormatterBuilder
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadExpired
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadFailed
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloading
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCItem

class PlayerTOCChapterAdapter(
  private val context: Context,
  private val book: PlayerAudioBookType,
  private val onSelect: (PlayerManifestTOCItem) -> Unit,
) : RecyclerView.Adapter<PlayerTOCChapterAdapter.ViewHolder>() {

  private var currentTOCIndex: Int = -1

  private val listener: View.OnClickListener =
    View.OnClickListener { v -> this.onSelect(v.tag as PlayerManifestTOCItem) }
  private val timeStrings: PlayerTimeStrings.SpokenTranslations =
    PlayerTimeStrings.SpokenTranslations.createFromResources(this.context.resources)

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

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): ViewHolder {
    PlayerUIThread.checkIsUIThread()

    val view =
      LayoutInflater.from(parent.context)
        .inflate(R.layout.player_toc_chapter_item_view, parent, false)

    return this.ViewHolder(view)
  }

  override fun getItemCount(): Int {
    return this.book.tableOfContents.tocItemsInOrder.size
  }

  override fun onBindViewHolder(
    holder: ViewHolder,
    position: Int
  ) {
    PlayerUIThread.checkIsUIThread()

    val tocItem =
      this.book.tableOfContents.tocItemsInOrder[position]

    holder.titleText.text =
      tocItem.title
    holder.downloadedDurationText.text =
      this.periodFormatter.print(tocItem.duration.toPeriod())

    val readingOrderItem =
      this.book.readingOrderByID[tocItem.readingOrderLink.id]!!

    var requiresDownload = false
    var failedDownload = false
    var downloading = false

    val status = readingOrderItem.downloadStatus
    holder.buttons.visibility = if (status !is PlayerReadingOrderItemDownloaded) {
      VISIBLE
    } else {
      GONE
    }

    when (status) {
      is PlayerReadingOrderItemNotDownloaded -> {
        holder.buttonsDownloading.visibility = INVISIBLE
        holder.buttonsDownloadFailed.visibility = INVISIBLE

        if (this.book.supportsStreaming) {
          holder.buttonsNotDownloadedNotStreamable.visibility = INVISIBLE
          holder.buttonsNotDownloadedStreamable.visibility = VISIBLE

          if (this.book.supportsIndividualChapterDownload) {
            holder.notDownloadedStreamableRefresh.setOnClickListener {
              this.book.downloadTasksByID[readingOrderItem.id]?.fetch()
            }
            holder.notDownloadedStreamableRefresh.contentDescription =
              this.context.getString(
                R.string.audiobook_accessibility_toc_download,
                position
              )
            holder.notDownloadedStreamableRefresh.isEnabled = true
          } else {
            holder.notDownloadedStreamableRefresh.contentDescription = null
            holder.notDownloadedStreamableRefresh.isEnabled = false
          }
        } else {
          holder.buttonsNotDownloadedNotStreamable.visibility = VISIBLE
          holder.buttonsNotDownloadedStreamable.visibility = INVISIBLE

          if (this.book.supportsIndividualChapterDownload) {
            holder.notDownloadedNotStreamableRefresh.setOnClickListener {
              this.book.downloadTasksByID[readingOrderItem.id]?.fetch()
            }
            holder.notDownloadedNotStreamableRefresh.contentDescription =
              this.context.getString(
                R.string.audiobook_accessibility_toc_download,
                position
              )
            holder.notDownloadedNotStreamableRefresh.isEnabled = true
          } else {
            holder.notDownloadedNotStreamableRefresh.contentDescription = null
            holder.notDownloadedNotStreamableRefresh.isEnabled = false
          }
          requiresDownload = true
        }
      }

      is PlayerReadingOrderItemDownloading -> {
        holder.buttonsDownloading.visibility = VISIBLE
        holder.buttonsDownloadFailed.visibility = INVISIBLE
        holder.buttonsNotDownloadedStreamable.visibility = INVISIBLE
        holder.buttonsNotDownloadedNotStreamable.visibility = INVISIBLE

        if (this.book.supportsIndividualChapterDownload) {
          holder.downloadingProgress.setOnClickListener {
            this.onConfirmCancelDownloading(readingOrderItem)
          }
          holder.downloadingProgress.isEnabled = true
        } else {
          holder.downloadingProgress.isEnabled = false
        }

        holder.downloadingProgress.contentDescription =
          this.context.getString(
            R.string.audiobook_accessibility_toc_progress,
            position,
            status.percent
          )
        holder.downloadingProgress.visibility = VISIBLE
        holder.downloadingProgress.progress = status.percent.toFloat() * 0.01f

        downloading = true
        requiresDownload = this.book.supportsStreaming == false
      }

      is PlayerReadingOrderItemDownloaded -> {
        holder.view.isEnabled = true
      }

      is PlayerReadingOrderItemDownloadFailed -> {
        holder.buttonsDownloading.visibility = INVISIBLE
        holder.buttonsDownloadFailed.visibility = VISIBLE
        holder.buttonsNotDownloadedStreamable.visibility = INVISIBLE
        holder.buttonsNotDownloadedNotStreamable.visibility = INVISIBLE

        if (this.book.supportsIndividualChapterDownload) {
          holder.downloadFailedRefresh.setOnClickListener {
            this.book.downloadTasksByID[readingOrderItem.id]?.cancel()
            this.book.downloadTasksByID[readingOrderItem.id]?.fetch()
          }
          holder.downloadFailedRefresh.contentDescription =
            this.context.getString(R.string.audiobook_accessibility_toc_retry, position)
          holder.downloadFailedRefresh.isEnabled = true
        } else {
          holder.downloadFailedRefresh.contentDescription = null
          holder.downloadFailedRefresh.isEnabled = false
        }

        failedDownload = true
        requiresDownload = this.book.supportsStreaming == false
      }

      is PlayerReadingOrderItemDownloadExpired -> {
        // Nothing to do.
      }
    }

    val view = holder.view
    view.tag = tocItem
    view.setOnClickListener(this.listener)
    view.contentDescription =
      contentDescriptionOf(
        resources = context.resources,
        title = tocItem.title,
        duration = tocItem.duration,
        playing = position == this.currentTOCIndex,
        requiresDownload = requiresDownload,
        failedDownload = failedDownload,
        downloading = downloading
      )

    if (position == this.currentTOCIndex) {
      holder.isCurrent.visibility = VISIBLE
    } else {
      holder.isCurrent.visibility = INVISIBLE
    }
  }

  private fun onConfirmCancelDownloading(
    readingOrderItem: PlayerReadingOrderItemType
  ) {
    val dialog =
      MaterialAlertDialogBuilder(this.context)
        .setCancelable(true)
        .setMessage(R.string.audiobook_part_download_stop_confirm)
        .setPositiveButton(
          R.string.audiobook_part_download_stop
        ) { _: DialogInterface, _: Int ->
          this.book.downloadTasksByID[readingOrderItem.id]?.cancel()
        }
        .setNegativeButton(
          R.string.audiobook_part_download_continue
        ) { _: DialogInterface, _: Int -> }
        .create()
    dialog.show()
  }

  private fun contentDescriptionOf(
    resources: Resources,
    title: String,
    duration: Duration?,
    playing: Boolean,
    requiresDownload: Boolean,
    failedDownload: Boolean,
    downloading: Boolean
  ): String {
    val builder = StringBuilder(128)

    if (playing) {
      builder.append(resources.getString(R.string.audiobook_accessibility_toc_chapter_is_current))
      builder.append(" ")
    }

    builder.append(title)
    builder.append(". ")

    if (duration != null) {
      builder.append(resources.getString(R.string.audiobook_accessibility_toc_chapter_duration_is))
      builder.append(" ")
      builder.append(
        PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(this.timeStrings, duration)
      )
      builder.append(". ")
    }

    if (requiresDownload) {
      builder.append(resources.getString(R.string.audiobook_accessibility_toc_chapter_requires_download))
      builder.append(".")
    }

    if (failedDownload) {
      builder.append(resources.getString(R.string.audiobook_accessibility_toc_chapter_failed_download))
      builder.append(".")
    }

    if (downloading) {
      builder.append(resources.getString(R.string.audiobook_accessibility_toc_chapter_downloading))
      builder.append(".")
    }

    return builder.toString()
  }

  fun setCurrentTOCItemIndex(index: Int) {
    PlayerUIThread.checkIsUIThread()

    val previous = this.currentTOCIndex
    this.currentTOCIndex = index
    this.notifyItemChanged(index)

    if (previous != -1) {
      this.notifyItemChanged(previous)
    }
  }

  fun update(status: PlayerReadingOrderItemDownloadStatus) {
    val index = findIndex(status)
    if (index != null) {
      this.notifyItemChanged(index)
    }
  }

  private fun findIndex(status: PlayerReadingOrderItemDownloadStatus): Int? {
    for (index in 0 until book.readingOrder.size) {
      if (book.readingOrder[index].id == status.readingOrderItem.id) {
        return index
      }
    }
    return null
  }

  inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {

    val buttons: ViewGroup =
      this.view.findViewById(R.id.player_toc_chapter_end_controls)
    val buttonsDownloadFailed: ViewGroup =
      this.buttons.findViewById(R.id.player_toc_chapter_item_buttons_error)
    val buttonsNotDownloadedNotStreamable: ViewGroup =
      this.buttons.findViewById(R.id.player_toc_chapter_item_buttons_not_downloaded_not_streamable)
    val buttonsNotDownloadedStreamable: ViewGroup =
      this.buttons.findViewById(R.id.player_toc_chapter_item_buttons_not_downloaded_streamable)
    val buttonsDownloading: ViewGroup =
      this.buttons.findViewById(R.id.player_toc_chapter_item_buttons_downloading)

    val titleText: TextView =
      this.view.findViewById(R.id.player_toc_chapter_item_view_title)
    val isCurrent: ImageView =
      this.view.findViewById(R.id.player_toc_chapter_item_is_current)

    val downloadFailedErrorIcon: ImageView =
      this.buttonsDownloadFailed.findViewById(R.id.player_toc_item_download_failed_error_icon)
    val downloadFailedRefresh: ImageView =
      this.buttonsDownloadFailed.findViewById(R.id.player_toc_item_download_failed_refresh)

    val downloadedDurationText: TextView =
      this.view.findViewById(R.id.player_toc_chapter_item_duration)

    val notDownloadedStreamableRefresh: ImageView =
      this.buttonsNotDownloadedStreamable.findViewById(
        R.id.player_toc_item_not_downloaded_streamable_refresh
      )
    val notDownloadedStreamableProgress: PlayerCircularProgressView =
      this.buttonsNotDownloadedStreamable.findViewById(
        R.id.player_toc_item_not_downloaded_streamable_progress
      )

    val notDownloadedNotStreamableRefresh: ImageView =
      this.buttonsNotDownloadedNotStreamable.findViewById(
        R.id.player_toc_item_not_downloaded_streamable_refresh
      )

    val downloadingProgress: PlayerCircularProgressView =
      this.buttonsDownloading.findViewById(R.id.player_toc_item_downloading_progress)

    init {
      this.downloadingProgress.thickness = 8.0f
      this.notDownloadedStreamableProgress.thickness = 8.0f
    }
  }
}
