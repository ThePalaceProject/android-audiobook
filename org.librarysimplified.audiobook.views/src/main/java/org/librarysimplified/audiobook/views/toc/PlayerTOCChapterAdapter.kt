package org.librarysimplified.audiobook.views.toc

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
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloadExpired
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloadFailed
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloaded
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloading
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementNotDownloaded
import org.librarysimplified.audiobook.api.PlayerSpineElementType
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.views.PlayerCircularProgressView
import org.librarysimplified.audiobook.views.PlayerTimeStrings
import org.librarysimplified.audiobook.views.R

/**
 * A Recycler view adapter used to display and control the chapters of the table of contents.
 */

class PlayerTOCChapterAdapter(
  private val context: Context,
  private val spineElements: List<PlayerSpineElementType>,
  private val downloadTasks: List<PlayerDownloadTaskType>,
  private val onSelect: (PlayerSpineElementType) -> Unit,
) :
  RecyclerView.Adapter<PlayerTOCChapterAdapter.ViewHolder>() {

  private val listener: View.OnClickListener
  private var currentSpineElement: Int = -1

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

  private val timeStrings: PlayerTimeStrings.SpokenTranslations

  init {
    this.timeStrings =
      PlayerTimeStrings.SpokenTranslations.createFromResources(this.context.resources)
    this.listener = View.OnClickListener { v -> this.onSelect(v.tag as PlayerSpineElementType) }
  }

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

  override fun onBindViewHolder(
    holder: ViewHolder,
    position: Int
  ) {
    PlayerUIThread.checkIsUIThread()

    val item = this.spineElements[position]
    val normalIndex = item.index + 1

    val title = item.title ?: this.context.getString(
      R.string.audiobook_player_toc_track_n,
      normalIndex
    )

    holder.titleText.text = title
    holder.titleText.isEnabled = false
    holder.downloadedDurationText.text =
      item.duration?.let {
        this.periodFormatter.print(it.toPeriod())
      }.orEmpty()
    holder.view.isEnabled = item.book.supportsStreaming

    var requiresDownload = false
    var failedDownload = false
    var downloading = false
    val status = item.downloadStatus
    holder.buttons.visibility = if (status !is PlayerSpineElementDownloaded) {
      VISIBLE
    } else {
      GONE
    }

    when (status) {
      is PlayerSpineElementNotDownloaded -> {
        holder.buttonsDownloading.visibility = INVISIBLE
        holder.buttonsDownloadFailed.visibility = INVISIBLE

        if (item.book.supportsStreaming) {
          holder.buttonsNotDownloadedNotStreamable.visibility = INVISIBLE
          holder.buttonsNotDownloadedStreamable.visibility = VISIBLE

          if (item.downloadTasksSupported) {
            holder.notDownloadedStreamableRefresh.setOnClickListener {
              downloadTasks.firstOrNull { task ->
                task.fulfillsSpineElement(item)
              }?.fetch()
            }
            holder.notDownloadedStreamableRefresh.contentDescription =
              this.context.getString(
                R.string.audiobook_accessibility_toc_download,
                normalIndex
              )
            holder.notDownloadedStreamableRefresh.isEnabled = true
          } else {
            holder.notDownloadedStreamableRefresh.contentDescription = null
            holder.notDownloadedStreamableRefresh.isEnabled = false
          }
        } else {
          holder.buttonsNotDownloadedNotStreamable.visibility = VISIBLE
          holder.buttonsNotDownloadedStreamable.visibility = INVISIBLE

          if (item.downloadTasksSupported) {
            holder.notDownloadedStreamableRefresh.setOnClickListener {
              downloadTasks.firstOrNull { task ->
                task.fulfillsSpineElement(item)
              }?.fetch()
            }
            holder.notDownloadedStreamableRefresh.contentDescription =
              this.context.getString(
                R.string.audiobook_accessibility_toc_download,
                normalIndex
              )
            holder.notDownloadedStreamableRefresh.isEnabled = true
          } else {
            holder.notDownloadedStreamableRefresh.contentDescription = null
            holder.notDownloadedStreamableRefresh.isEnabled = false
          }

          requiresDownload = true
        }
      }

      is PlayerSpineElementDownloading -> {
        holder.buttonsDownloading.visibility = VISIBLE
        holder.buttonsDownloadFailed.visibility = INVISIBLE
        holder.buttonsNotDownloadedStreamable.visibility = INVISIBLE
        holder.buttonsNotDownloadedNotStreamable.visibility = INVISIBLE

        if (item.downloadTasksSupported) {
          holder.downloadingProgress.setOnClickListener { this.onConfirmCancelDownloading(item) }
          holder.downloadingProgress.isEnabled = true
        } else {
          holder.downloadingProgress.isEnabled = false
        }

        holder.downloadingProgress.contentDescription =
          this.context.getString(R.string.audiobook_accessibility_toc_progress, normalIndex, status.percent)
        holder.downloadingProgress.visibility = VISIBLE
        holder.downloadingProgress.progress = status.percent.toFloat() * 0.01f

        downloading = true
        requiresDownload = item.book.supportsStreaming == false
      }

      is PlayerSpineElementDownloaded -> {
        holder.view.isEnabled = true
      }

      is PlayerSpineElementDownloadFailed -> {
        holder.buttonsDownloading.visibility = INVISIBLE
        holder.buttonsDownloadFailed.visibility = VISIBLE
        holder.buttonsNotDownloadedStreamable.visibility = INVISIBLE
        holder.buttonsNotDownloadedNotStreamable.visibility = INVISIBLE

        if (item.downloadTasksSupported) {
          holder.downloadFailedRefresh.setOnClickListener {
            val task = downloadTasks.firstOrNull { task ->
              task.fulfillsSpineElement(item)
            }
            task?.cancel()
            task?.fetch()
          }
          holder.downloadFailedRefresh.contentDescription =
            this.context.getString(R.string.audiobook_accessibility_toc_retry, normalIndex)
          holder.downloadFailedRefresh.isEnabled = true
        } else {
          holder.downloadFailedRefresh.contentDescription = null
          holder.downloadFailedRefresh.isEnabled = false
        }

        failedDownload = true
        requiresDownload = item.book.supportsStreaming == false
      }

      is PlayerSpineElementDownloadExpired -> {
        // Nothing to do.
      }
    }

    val view = holder.view
    view.tag = item
    view.setOnClickListener(this@PlayerTOCChapterAdapter.listener)
    view.contentDescription =
      contentDescriptionOf(
        resources = context.resources,
        title = title,
        duration = item.duration,
        playing = position == this.currentSpineElement,
        requiresDownload = requiresDownload,
        failedDownload = failedDownload,
        downloading = downloading
      )

    if (position == this.currentSpineElement) {
      holder.isCurrent.visibility = VISIBLE
    } else {
      holder.isCurrent.visibility = INVISIBLE
    }
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

  private fun onConfirmCancelDownloading(item: PlayerSpineElementType) {
    val dialog =
      MaterialAlertDialogBuilder(this.context)
        .setCancelable(true)
        .setMessage(R.string.audiobook_part_download_stop_confirm)
        .setPositiveButton(
          R.string.audiobook_part_download_stop,
          { _: DialogInterface, _: Int ->
            downloadTasks.firstOrNull { task ->
              task.fulfillsSpineElement(item)
            }?.cancel()
          }
        )
        .setNegativeButton(
          R.string.audiobook_part_download_continue,
          { _: DialogInterface, _: Int -> }
        )
        .create()
    dialog.show()
  }

  override fun getItemCount(): Int = this.spineElements.size

  fun setCurrentSpineElement(index: Int) {
    PlayerUIThread.checkIsUIThread()

    val previous = this.currentSpineElement
    this.currentSpineElement = index
    this.notifyItemChanged(index)

    if (previous != -1) {
      this.notifyItemChanged(previous)
    }
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
        R.id.player_toc_item_not_downloaded_not_streamable_refresh
      )

    val downloadingProgress: PlayerCircularProgressView =
      this.buttonsDownloading.findViewById(R.id.player_toc_item_downloading_progress)

    init {
      this.downloadingProgress.thickness = 8.0f

      this.notDownloadedStreamableProgress.thickness = 8.0f
    }
  }
}
