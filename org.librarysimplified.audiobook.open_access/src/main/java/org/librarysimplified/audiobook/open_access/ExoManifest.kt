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

    fun transform(
      context: Context,
      manifest: PlayerManifest
    ): PlayerResult<ExoManifest, Exception> {
      try {

        val spineItems = if (!manifest.toc.isNullOrEmpty()) {
          getSpineItemsFromTOC(context, manifest.readingOrder, manifest.toc!!)
        } else {
          manifest.readingOrder.mapIndexed { index, item ->
            this.processSpineItem(context, index, item)
          }
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

    private val OCTET_STREAM =
      MIMEType("application", "octet-stream", mapOf())

    private fun getOffsetFromTocElement(tocElement: PlayerManifestLink): Double {
      val offsetStr = "#t="
      val href = tocElement.hrefURI.toString()
      val offsetIndex = href.indexOf(offsetStr)
      return if (offsetIndex != -1) {
        href.substring(offsetIndex + offsetStr.length).toDouble()
      } else {
        0.0
      }
    }

    private fun getHrefWithoutOffset(tocElement: PlayerManifestLink): String {
      val offsetStr = "#t="
      val href = tocElement.hrefURI.toString()
      val offsetIndex = href.indexOf(offsetStr)
      return if (offsetIndex != -1) {
        href.substring(0, offsetIndex)
      } else {
        href
      }
    }

    private fun getSpineItemsFromTOC(
      context: Context,
      readingOrderElements: List<PlayerManifestLink>,
      tocElements: List<PlayerManifestLink>
    ): List<ExoManifestSpineItem> {

      val spineItems = arrayListOf<ExoManifestSpineItem>()

      val allElementsOfTOC = arrayListOf<PlayerManifestLink>()
      tocElements.forEach { tocElement ->
        allElementsOfTOC.add(tocElement)
        allElementsOfTOC.addAll(tocElement.children.orEmpty())
      }

      allElementsOfTOC.forEachIndexed { index, tocElement ->
        val href = this.getHrefWithoutOffset(tocElement)

        val readingOrderItem = readingOrderElements.firstOrNull { readingOrderElement ->
          href == readingOrderElement.hrefURI.toString()
        }

        val tocElementOffset = getOffsetFromTocElement(tocElement)
        val wholeDuration = readingOrderItem?.duration ?: -1.0

        val tocElementDuration = when {
          index != allElementsOfTOC.lastIndex -> {

            val nextTocElement = allElementsOfTOC[index + 1]

            when {

              // if the next toc element has the same href, then it belongs to the same "parent"
              this.getHrefWithoutOffset(nextTocElement) == href -> {
                getOffsetFromTocElement(nextTocElement) - tocElementOffset
              }

              // if the next toc element hasn't the same href, then it belongs to another "parent" and
              //we need to use the current item's parent duration
              wholeDuration != -1.0 -> {
                wholeDuration.toInt() - tocElementOffset
              }

              // fallback
              else -> {
                tocElementOffset
              }
            }

          }
          wholeDuration != -1.0 -> {
            wholeDuration.toInt() - tocElementOffset

            // fallback
          }
          else -> {
            tocElementOffset
          }
        }

        spineItems.add(
          ExoManifestSpineItem(
            chapter = index,
            duration = tocElementDuration,
            offset = tocElementOffset,
            originalLink = readingOrderItem ?: tocElement,
            part = 0,
            title = tocElement.title
              ?: context.getString(R.string.player_manifest_audiobook_default_track_n, index + 1),
            type = readingOrderItem?.type ?: OCTET_STREAM,
            uri = if (readingOrderItem != null) {
              this.parseURI(readingOrderItem, index)
            } else {
              this.parseURI(tocElement, index)
            }
          )
        )
      }

      return spineItems
    }

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
        offset = 0.0,
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
