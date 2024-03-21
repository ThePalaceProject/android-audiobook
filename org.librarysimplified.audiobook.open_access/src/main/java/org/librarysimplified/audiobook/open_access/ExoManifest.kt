package org.librarysimplified.audiobook.open_access

import android.content.Context
import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderItem
import org.librarysimplified.audiobook.manifest.api.R

/**
 * A manifest transformed such that it contains information relevant to the Exo audio engine.
 */

data class ExoManifest(
  val context: Context,
  val title: String,
  val id: String,
  val spineItems: List<ExoManifestSpineItem>
) {

  companion object {

    private val OCTET_STREAM =
      MIMEType("application", "octet-stream", mapOf())

    /**
     * Parse an ExoPlayer manifest from the given raw manifest.
     */

    fun transform(
      context: Context,
      manifest: PlayerManifest
    ): PlayerResult<ExoManifest, Exception> {
      try {
        val spineItems =
          manifest.readingOrder.mapIndexed { index, item ->
            this.processSpineItem(context, index, item)
          }

        return PlayerResult.Success(
          ExoManifest(
            context,
            manifest.metadata.title,
            manifest.metadata.identifier,
            spineItems
          )
        )
      } catch (e: Exception) {
        return PlayerResult.Failure(e)
      }
    }

    private fun processSpineItem(
      context: Context,
      index: Int,
      item: PlayerManifestReadingOrderItem
    ): ExoManifestSpineItem {
      val link = item.link

      val title = if (!link.title.isNullOrBlank()) {
        link.title
      } else {
        context.getString(R.string.player_manifest_audiobook_default_track_n, index + 1)
      }

      val type =
        link.type ?: OCTET_STREAM

      return ExoManifestSpineItem(
        title = title,
        part = 0,
        offset = 0.0,
        chapter = index,
        type = type,
        uri = link.hrefURI!!,
        originalLink = item,
        duration = link.duration
      )
    }
  }
}
