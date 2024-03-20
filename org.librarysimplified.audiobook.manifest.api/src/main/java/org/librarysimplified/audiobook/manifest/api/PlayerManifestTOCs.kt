package org.librarysimplified.audiobook.manifest.api

import com.io7m.kabstand.core.IntervalL
import com.io7m.kabstand.core.IntervalTree
import com.io7m.kabstand.core.IntervalTreeDebuggableType
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
     * Our goal here is to construct a table of contents for display, and to put together
     * the structures needed to to efficiently return a TOC item given a reading order item
     * and a relative offset in seconds. We'll do this by transforming the start times
     * and durations of all reading order and TOC items into points on an absolute timeline
     * representing the entirety of the book, and then construct the mappings needed to efficiently
     * go from "a reading order item and an offset from the start of that item in seconds" to
     * "a TOC item, and an offset in seconds from the start of that TOC item".
     *
     * The steps we'll need to (frequently) perform are:
     *
     * 1. For a given reading order item `i` and a relative offset `t` in seconds from the
     *    start of `t`, transform to an absolute time `u`. We can calculate `u` by simply looking
     *    up the absolute interval of `i` in the map we construct, and adding the absolute start
     *    time of `i` to `t`.
     *
     * 2. With the calculated absolute time in seconds `u`, perform a lookup to determine which
     *    TOC item intersects `u`. To do this lookup in O(log n) in the average case, we'll need to
     *    use an interval tree to store the intervals of the TOC items. We could brute-force the
     *    lookup, but that doesn't seem like a very good idea given the frequency that we'll need
     *    to perform these lookups.
     *
     * 3. When we know which TOC item `u` falls within, we can get the relative offset of `u`
     *    by subtracting the absolute start time of the TOC item from `u`.
     */

    /*
     * Recursively flatten the TOC, associating each item with its parent item.
     */

    val tocItemsFlattened = arrayListOf<TOCMember>()
    this.flattenTOC(
      parent = null,
      inputItems = manifest.toc,
      outputItems = tocItemsFlattened
    )

    val readingOrderIntervals =
      mutableMapOf<URI, IntervalL>()
    val readingOrderByURI =
      mutableMapOf<URI, PlayerManifestLink>()

    /*
     * Place all of the reading order elements on an absolute timeline. The result is a mapping
     * where, if we know the reading order item, then we know the absolute start and end point
     * of the item in seconds across the entire book.
     */

    var readingOrderAbsoluteTime = 0L
    manifest.readingOrder.forEach { link ->
      val duration = (link.duration ?: 0L).toLong()
      val lower = readingOrderAbsoluteTime
      val upper = readingOrderAbsoluteTime + Math.max(0L, duration - 1)
      val interval = IntervalL(lower, upper)
      readingOrderAbsoluteTime = upper + 1
      readingOrderIntervals[link.hrefURI!!] = interval
      readingOrderByURI[link.hrefURI!!] = link
    }

    /*
     * Now place all of the TOC items on the same absolute timeline. As with reading order
     * items, we build an interval tree so that we can look up TOC items by absolute time.
     */

    val tocItemTree =
      IntervalTree.empty<Long>()
    val tocItemsByInterval =
      mutableMapOf<IntervalL, PlayerManifestTOCItem>()
    val tocItemsInOrder =
      mutableListOf<PlayerManifestTOCItem>()

    for (index in 0 until tocItemsFlattened.size) {
      val tocMember =
        tocItemsFlattened[index]
      val withOffset =
        extractURIOffset(tocMember.item.hrefURI!!)
      val readingOrderInterval =
        readingOrderIntervals[withOffset.uriWithoutOffset]!!
      val readingOrderItem =
        readingOrderByURI[withOffset.uriWithoutOffset]!!

      /*
       * The lower bound for this TOC item's interval is the absolute time of the start of the
       * reading order item, plus any offset specified in the TOC item's URI.
       */

      val lowerOffset =
        withOffset.offset
      val lower =
        readingOrderInterval.lower + lowerOffset

      /*
       * The upper bound for this TOC item's interval is either one less than the lower bound of
       * the next TOC item, or it is the upper bound of all remaining reading order items.
       */

      val upper =
        if (index + 1 < tocItemsFlattened.size) {
          val tocMemberNext =
            tocItemsFlattened[index + 1]
          val withOffsetNext =
            extractURIOffset(tocMemberNext.item.hrefURI!!)
          val readingOrderIntervalNext =
            readingOrderIntervals[withOffsetNext.uriWithoutOffset]!!
          val upperOffset =
            withOffsetNext.offset
          readingOrderIntervalNext.lower + (upperOffset - 1)
        } else {
          readingOrderAbsoluteTime - 1
        }

      /*
       * Manifests with (broken) zero-length TOC items can cause us to determine incorrect bounds.
       * If this happens, we simply refuse to create a nonsensical TOC item.
       */

      if (upper < lower) {
        continue
      }

      val tocInterval =
        IntervalL(lower, upper)

      val tocItem =
        PlayerManifestTOCItem(
          title = readingOrderItem.title ?: defaultTrackTitle.invoke(index + 1),
          part = 1,
          chapter = index + 1,
          intervalAbsoluteSeconds = tocInterval
        )

      tocItemsByInterval[tocInterval] = tocItem
      tocItemsInOrder.add(tocItem)
      insertIntervalChecked(tocItemTree, tocInterval)

      /*
       * Some books have a special case where the first TOC item points to something that
       * isn't the first reading order item. We must inject a fake TOC item that covers all
       * of the reading items prior to the first real TOC item.
       */

      if (index == 0) {
        if (readingOrderItem != manifest.readingOrder[0]) {
          val tocItemFake =
            PlayerManifestTOCItem(
              title = "[Preamble]",
              part = 1,
              chapter = 0,
              intervalAbsoluteSeconds = IntervalL(0L, lower - 1L),
            )

          tocItemsByInterval[tocItemFake.intervalAbsoluteSeconds] = tocItemFake
          tocItemsInOrder.add(0, tocItemFake)
          insertIntervalChecked(tocItemTree, tocItemFake.intervalAbsoluteSeconds)
        }
      }
    }

    return PlayerManifestTOC(
      tocItemsInOrder = tocItemsInOrder.toList(),
      tocItemsByInterval = tocItemsByInterval.toMap(),
      tocItemTree = tocItemTree,
      readingOrderIntervals = readingOrderIntervals
    )
  }

  private fun insertIntervalChecked(
    tocItemTree: IntervalTreeDebuggableType<Long>,
    tocInterval: IntervalL
  ) {
    val overlapping = tocItemTree.overlapping(tocInterval)
    check(overlapping.isEmpty()) {
      "Inserted TOC interval must not overlap any other intervals ($tocInterval overlaps $overlapping)"
    }
    tocItemTree.add(tocInterval)
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
          matcher.group(1)?.toLong() ?: 0L
        } else {
          0L
        }
      } else {
        0L
      }

    return URIWithOffset(withoutOffset, offset)
  }

  private data class URIWithOffset(
    val uriWithoutOffset: URI,
    val offset: Long
  )

  private fun buildTOCFromManifestReadingOrder(
    manifest: PlayerManifest,
    defaultTrackTitle: (Int) -> String
  ): PlayerManifestTOC {
    check(manifest.toc.isNullOrEmpty()) { "TOC must be null or empty" }

    var offset = 0L

    val tocItemTree =
      IntervalTree.empty<Long>()
    val tocItemsByInterval =
      mutableMapOf<IntervalL, PlayerManifestTOCItem>()
    val tocItemsInOrder =
      mutableListOf<PlayerManifestTOCItem>()
    val readingOrderIntervals =
      mutableMapOf<URI, IntervalL>()

    manifest.readingOrder.forEachIndexed { index, item ->
      val tocItem =
        this.buildTOCItem(
          index = index,
          item = item,
          offset = offset,
          defaultTrackTitle = defaultTrackTitle
        )
      offset += (item.duration ?: 1L).toLong()
      tocItemsInOrder.add(tocItem)
      tocItemsByInterval[tocItem.intervalAbsoluteSeconds] = tocItem
      tocItemTree.add(tocItem.intervalAbsoluteSeconds)
      readingOrderIntervals[item.hrefURI!!] = tocItem.intervalAbsoluteSeconds
    }

    return PlayerManifestTOC(
      tocItemsInOrder = tocItemsInOrder.toList(),
      tocItemsByInterval = tocItemsByInterval.toMap(),
      tocItemTree = tocItemTree,
      readingOrderIntervals = readingOrderIntervals
    )
  }

  private fun buildTOCItem(
    index: Int,
    item: PlayerManifestLink,
    offset: Long,
    defaultTrackTitle: (Int) -> String
  ): PlayerManifestTOCItem {
    val title =
      titleOrDefault(item, defaultTrackTitle, index)

    return PlayerManifestTOCItem(
      title = title,
      part = 1,
      chapter = index,
      intervalAbsoluteSeconds = IntervalL(offset, offset + (item.duration ?: 1).toLong())
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
