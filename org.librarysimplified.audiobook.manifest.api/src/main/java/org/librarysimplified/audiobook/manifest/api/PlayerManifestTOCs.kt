package org.librarysimplified.audiobook.manifest.api

import one.irradia.mime.api.MIMEType
import java.net.URI
import java.util.regex.Pattern

/**
 * Functions to linearize a manifest.
 */

object PlayerManifestTOCs {

  private val OCTET_STREAM =
    MIMEType("application", "octet-stream", mapOf())

  fun createTOC(
    manifest: PlayerManifest,
    defaultTrackTitle: (Int) -> String
  ): PlayerManifestTOC {
    if (manifest.toc.isNullOrEmpty()) {
      return this.buildTOCFromManifestReadingOrder(manifest, defaultTrackTitle)
    }
    return this.buildTOCFromManifestTOC(manifest, defaultTrackTitle)
  }

  private data class TOCMember(
    val parent: TOCMember?,
    val item: PlayerManifestLink
  )

  private fun buildTOCFromManifestTOC(
    manifest: PlayerManifest,
    defaultTrackTitle: (Int) -> String
  ): PlayerManifestTOC {
    check(!manifest.toc.isNullOrEmpty()) { "TOC must not be null or empty" }

    /*
     * Recursively flatten the TOC, associating each item with its parent item.
     */

    val tocItemsFlattened = arrayListOf<TOCMember>()
    this.flattenTOC(
      parent = null,
      inputItems = manifest.toc,
      outputItems = tocItemsFlattened
    )

    return PlayerManifestTOC(listOf())
  }

  private fun nextTOCEntryNonexistent(
    tocItems: List<TOCMember>,
    index: Int
  ): Boolean {
    return index + 1 >= tocItems.size
  }

  private fun flattenTOC(
    parent: TOCMember?,
    inputItems: List<PlayerManifestLink>,
    outputItems: ArrayList<TOCMember>
  ) {
    for (item in inputItems) {
      val currentMember = TOCMember(parent, item)
      outputItems.add(currentMember)
      val children = item.children
      if (children != null) {
        this.flattenTOC(
          parent = currentMember,
          inputItems = children,
          outputItems = outputItems
        )
      }
    }
  }

  private val uriFragmentOffsetPattern =
    Pattern.compile("t=([0-9]+)")

  private fun extractURIOffset(uri: URI): URIWithOffset {
    val fragment =
      uri.fragment
    val withoutOffset =
      URI(uri.scheme, uri.host, uri.path, null)

    val offset =
      if (fragment != null) {
        val matcher = uriFragmentOffsetPattern.matcher(fragment)
        if (matcher.matches()) {
          matcher.group(1)?.toLong()
        } else {
          null
        }
      } else {
        null
      }

    return URIWithOffset(withoutOffset, offset)
  }

  private data class URIWithOffset(
    val uriWithoutOffset: URI,
    val offset: Long?
  )

  private fun buildTOCFromManifestReadingOrder(
    manifest: PlayerManifest,
    defaultTrackTitle: (Int) -> String
  ): PlayerManifestTOC {
    check(manifest.toc.isNullOrEmpty()) { "TOC must be null or empty" }
    return PlayerManifestTOC(
      manifest.readingOrder.mapIndexed { index, item ->
        this.buildTOCItem(
          index = index,
          item = item,
          defaultTrackTitle = defaultTrackTitle,
          offset = 0.0
        )
      }
    )
  }

  private fun buildTOCItem(
    index: Int,
    item: PlayerManifestLink,
    defaultTrackTitle: (Int) -> String,
    offset: Double,
    duration: Double? = item.duration
  ): PlayerManifestTOCItem {
    val title = titleOrDefault(item, defaultTrackTitle, index)

    val type =
      item.type ?: this.OCTET_STREAM
    val uri =
      this.parseURI(item, index)

    return PlayerManifestTOCItem(
      title = title,
      part = 0,
      offset = offset,
      chapter = index,
      type = type,
      uri = uri,
      originalLink = item,
      duration = duration
    )
  }

  private fun titleOrDefault(
    item: PlayerManifestLink,
    defaultTrackTitle: (Int) -> String,
    index: Int
  ): String {
    return if (!item.title.isNullOrBlank()) {
      item.title!!
    } else {
      defaultTrackTitle.invoke(index + 1)
    }
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
