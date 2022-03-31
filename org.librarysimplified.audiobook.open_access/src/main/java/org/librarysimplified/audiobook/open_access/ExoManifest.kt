package org.librarysimplified.audiobook.open_access

import android.content.Context
import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.manifest.api.R
import java.net.URI

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

    /**
     * Parse an ExoPlayer manifest from the given raw manifest.
     */

    fun transform(context: Context, manifest: PlayerManifest): PlayerResult<ExoManifest, Exception> {
      try {

        val spineItems = if (manifest.toc != null) {
          val items = arrayListOf<PlayerManifestLink>()
          manifest.toc?.forEach { tocElement ->
            if (!tocElement.children.isNullOrEmpty()) {
              items.addAll(tocElement.children.orEmpty())
            } else {
              items.add(tocElement)
            }
          }
          items
        } else {
          manifest.readingOrder
        }
        return PlayerResult.Success(
          ExoManifest(
            context,
            manifest.metadata.title,
            manifest.metadata.identifier,
            spineItems.mapIndexed { index, item ->
              this.processSpineItem(context, index, item)
            }
          )
        )
      } catch (e: Exception) {
        return PlayerResult.Failure(e)
      }
    }

    private val OCTET_STREAM =
      MIMEType("application", "octet-stream", mapOf())

    private fun processSpineItem(
      context: Context,
      index: Int,
      item: PlayerManifestLink
    ): ExoManifestSpineItem {

      val title = if (!item.title.isNullOrBlank()) {
        item.title
      } else {
        context.getString(R.string.player_manifest_audiobook_default_track_n, index + 1)
      }

      val type =
        item.type ?: this.OCTET_STREAM
      val uri =
        this.parseURI(item, index)

      return ExoManifestSpineItem(
        title = title,
        part = 0,
        chapter = index,
        type = type,
        uri = uri,
        originalLink = item,
        duration = item.duration
      )
    }

    private fun parseURI(
      link: PlayerManifestLink,
      index: Int
    ): URI {
      return when (link) {
        is PlayerManifestLink.LinkBasic ->
          link.href ?: throw IllegalArgumentException("Spine item $index has a null 'href' field")
        is PlayerManifestLink.LinkTemplated ->
          throw IllegalArgumentException("Spine item $index has a templated 'href' field")
      }
    }
  }
}
