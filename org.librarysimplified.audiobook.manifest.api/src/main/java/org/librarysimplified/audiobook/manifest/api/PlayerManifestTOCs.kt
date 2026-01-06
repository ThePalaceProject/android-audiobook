package org.librarysimplified.audiobook.manifest.api

import com.io7m.kabstand.core.IntervalTree
import com.io7m.kabstand.core.IntervalTreeDebuggableType
import org.joda.time.Duration
import java.net.URI
import java.util.regex.Pattern

/**
 * Functions to linearize a manifest.
 */

object PlayerManifestTOCs {

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
     * and a relative offset in milliseconds. We'll do this by transforming the start times
     * and durations of all reading order and TOC items into points on an absolute timeline
     * representing the entirety of the book, and then construct the mappings needed to efficiently
     * go from "a reading order item and an offset from the start of that item in milliseconds" to
     * "a TOC item, and an offset in milliseconds from the start of that TOC item".
     *
     * The steps we'll need to (frequently) perform are:
     *
     * 1. For a given reading order item `i` and a relative offset `t` in milliseconds from the
     *    start of `t`, transform to an absolute time `u`. We can calculate `u` by simply looking
     *    up the absolute interval of `i` in the map we construct, and adding the absolute start
     *    time of `i` to `t`.
     *
     * 2. With the calculated absolute time in milliseconds `u`, perform a lookup to determine which
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

    val readingOrderItemTree =
      IntervalTree.empty<PlayerMillisecondsAbsolute>()
    val readingOrderIntervals =
      mutableMapOf<PlayerManifestReadingOrderID, PlayerMillisecondsAbsoluteInterval>()
    val readingOrderByID =
      mutableMapOf<PlayerManifestReadingOrderID, PlayerManifestReadingOrderItem>()
    val readingOrderItemsByInterval =
      mutableMapOf<PlayerMillisecondsAbsoluteInterval, PlayerManifestReadingOrderID>()

    /*
     * Place all of the reading order elements on an absolute timeline. The result is a mapping
     * where, if we know the reading order item, then we know the absolute start and end point
     * of the item in milliseconds across the entire book.
     */

    var readingOrderAbsoluteTime = 0L
    manifest.readingOrder.forEach { item ->
      val link = item.link
      val duration = Duration.standardSeconds((link.duration ?: 0L).toLong())
      val lower = readingOrderAbsoluteTime
      val upper = readingOrderAbsoluteTime + Math.max(0L, duration.millis - 1)
      val interval =
        PlayerMillisecondsAbsoluteInterval(
          lower = PlayerMillisecondsAbsolute(lower),
          upper = PlayerMillisecondsAbsolute(upper)
        )

      readingOrderItemTree.add(interval)
      readingOrderAbsoluteTime = upper + 1
      readingOrderIntervals[item.id] = interval
      readingOrderByID[item.id] = item
      readingOrderItemsByInterval[interval] = item.id
    }

    /*
     * Now place all of the TOC items on the same absolute timeline. We build an interval tree so
     * that we can look up TOC items by absolute time.
     */

    val tocItemTree =
      IntervalTree.empty<PlayerMillisecondsAbsolute>()
    val tocItemsByInterval =
      mutableMapOf<PlayerMillisecondsAbsoluteInterval, PlayerManifestTOCItem>()
    val tocItemsInOrder =
      mutableListOf<PlayerManifestTOCItem>()

    var tocIndex = 0

    for (tocEntryIndex in 0 until tocItemsFlattened.size) {
      val tocMember =
        tocItemsFlattened[tocEntryIndex]
      val withOffset =
        extractURIOffset(tocMember.item.hrefURI!!)
      val readingOrderInterval =
        findReadingOrderInterval(readingOrderIntervals, withOffset)
      val readingOrderItem =
        findReadingOrderItem(readingOrderByID, withOffset)

      /*
       * The lower bound for this TOC item's interval is the absolute time of the start of the
       * reading order item, plus any offset specified in the TOC item's URI.
       */

      val lowerOffset =
        withOffset.offset
      val lower: PlayerMillisecondsAbsolute =
        PlayerMillisecondsAbsolute(readingOrderInterval.lower.value + lowerOffset.millis)

      /*
       * The upper bound for this TOC item's interval is either one less than the lower bound of
       * the next TOC item, or it is the upper bound of all remaining reading order items.
       */

      val upper: PlayerMillisecondsAbsolute =
        if (tocEntryIndex + 1 < tocItemsFlattened.size) {
          val tocMemberNext =
            tocItemsFlattened[tocEntryIndex + 1]
          val withOffsetNext =
            extractURIOffset(tocMemberNext.item.hrefURI!!)
          val readingOrderIntervalNext =
            findReadingOrderInterval(readingOrderIntervals, withOffsetNext)
          val upperOffset =
            withOffsetNext.offset

          PlayerMillisecondsAbsolute(
            Math.max(0L, readingOrderIntervalNext.lower.value + (upperOffset.millis - 1L))
          )
        } else {
          PlayerMillisecondsAbsolute(readingOrderAbsoluteTime - 1L)
        }

      /*
       * Manifests with (broken) zero-length TOC items can cause us to determine incorrect bounds.
       * If this happens, we simply refuse to create a nonsensical TOC item.
       */

      if (upper < lower) {
        continue
      }

      val tocInterval =
        PlayerMillisecondsAbsoluteInterval(lower, upper)

      /*
       * Some books have a special case where the first TOC item points to something that
       * isn't the first reading order item. We must inject a fake TOC item that covers all
       * of the reading items prior to the first real TOC item.
       */

      if (tocEntryIndex == 0) {
        if (readingOrderItem != manifest.readingOrder[0]) {
          val interval =
            PlayerMillisecondsAbsoluteInterval(
              lower = PlayerMillisecondsAbsolute(0),
              upper = PlayerMillisecondsAbsolute(lower.value - 1L)
            )

          val tocItemFake =
            PlayerManifestTOCItem(
              index = tocIndex,
              title = "[Preamble]",
              chapter = 0,
              intervalAbsoluteMilliseconds = interval
            )

          tocItemsByInterval[tocItemFake.intervalAbsoluteMilliseconds] = tocItemFake
          tocItemsInOrder.add(0, tocItemFake)
          insertIntervalChecked(tocItemTree, tocItemFake.intervalAbsoluteMilliseconds)
          ++tocIndex
        }
      }

      val title: String =
        if (tocMember.item.title != null) {
          tocMember.item.title!!
        } else {
          readingOrderItem.link.title
            ?: defaultTrackTitle.invoke(tocEntryIndex)
        }

      val tocItem =
        PlayerManifestTOCItem(
          index = tocIndex,
          title = title,
          chapter = tocEntryIndex + 1,
          intervalAbsoluteMilliseconds = tocInterval
        )

      tocItemsByInterval[tocInterval] = tocItem
      tocItemsInOrder.add(tocItem)
      insertIntervalChecked(tocItemTree, tocInterval)
      ++tocIndex
    }

    return PlayerManifestTOC(
      tocItemsInOrder = tocItemsInOrder.toList(),
      tocItemsByInterval = tocItemsByInterval.toMap(),
      tocItemTree = tocItemTree,
      readingOrderIntervals = readingOrderIntervals,
      readingOrderItemsByInterval = readingOrderItemsByInterval,
      readingOrderItemTree = readingOrderItemTree
    )
  }

  private fun findReadingOrderItem(
    readingOrderByID: MutableMap<PlayerManifestReadingOrderID, PlayerManifestReadingOrderItem>,
    withOffset: URIWithOffset
  ): PlayerManifestReadingOrderItem {
    val r0 = readingOrderByID[withOffset.uriWithoutOffset]
    if (r0 != null) {
      return r0
    }

    /*
     * Some questionably-valid manifests will contain TOC elements that have fully qualified
     * URIs in some elements in the `toc` section.
     */

    val parsedURI =
      URI.create(withOffset.uriWithoutOffset.text)
    val pathSegments =
      parsedURI.path.split("/")
    val lastFile =
      pathSegments.last()

    val r1 = readingOrderByID[PlayerManifestReadingOrderID(lastFile)]
    if (r1 != null) {
      return r1
    }

    throw IllegalStateException(
      "Invalid audiobook manifest: Could not find a TOC (ID) entry for ${withOffset.uriWithoutOffset}"
    )
  }

  private fun findReadingOrderInterval(
    readingOrderIntervals: MutableMap<PlayerManifestReadingOrderID, PlayerMillisecondsAbsoluteInterval>,
    withOffset: URIWithOffset
  ): PlayerMillisecondsAbsoluteInterval {
    val r0 = readingOrderIntervals[withOffset.uriWithoutOffset]
    if (r0 != null) {
      return r0
    }

    /*
     * Some questionably-valid manifests will contain TOC elements that have fully qualified
     * URIs in some elements in the `toc` section.
     */

    val parsedURI =
      URI.create(withOffset.uriWithoutOffset.text)
    val pathSegments =
      parsedURI.path.split("/")
    val lastFile =
      pathSegments.last()

    val r1 = readingOrderIntervals[PlayerManifestReadingOrderID(lastFile)]
    if (r1 != null) {
      return r1
    }

    throw IllegalStateException(
      "Invalid audiobook manifest: Could not find a TOC (interval) entry for ${withOffset.uriWithoutOffset}"
    )
  }

  private fun insertIntervalChecked(
    tocItemTree: IntervalTreeDebuggableType<PlayerMillisecondsAbsolute>,
    tocInterval: PlayerMillisecondsAbsoluteInterval
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

  private fun extractURIOffset(
    uri: URI
  ): URIWithOffset {
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

    return URIWithOffset(
      PlayerManifestReadingOrderID(withoutOffset.toString()),
      Duration.standardSeconds(offset)
    )
  }

  private data class URIWithOffset(
    val uriWithoutOffset: PlayerManifestReadingOrderID,
    val offset: Duration
  )

  private fun buildTOCFromManifestReadingOrder(
    manifest: PlayerManifest,
    defaultTrackTitle: (Int) -> String
  ): PlayerManifestTOC {
    check(manifest.toc.isNullOrEmpty()) { "TOC must be null or empty" }

    var offset = 0L

    val tocItemTree =
      IntervalTree.empty<PlayerMillisecondsAbsolute>()
    val tocItemsByInterval =
      mutableMapOf<PlayerMillisecondsAbsoluteInterval, PlayerManifestTOCItem>()
    val tocItemsInOrder =
      mutableListOf<PlayerManifestTOCItem>()
    val readingOrderItemTree =
      IntervalTree.empty<PlayerMillisecondsAbsolute>()
    val readingOrderIntervals =
      mutableMapOf<PlayerManifestReadingOrderID, PlayerMillisecondsAbsoluteInterval>()
    val readingOrderItemsByInterval =
      mutableMapOf<PlayerMillisecondsAbsoluteInterval, PlayerManifestReadingOrderID>()

    manifest.readingOrder.forEachIndexed { index, item ->
      val tocItem =
        this.buildTOCItem(
          index = index,
          item = item,
          offset = PlayerMillisecondsAbsolute(offset),
          defaultTrackTitle = defaultTrackTitle
        )
      offset += tocItem.duration.millis
      tocItemsInOrder.add(tocItem)
      tocItemsByInterval[tocItem.intervalAbsoluteMilliseconds] = tocItem
      insertIntervalChecked(tocItemTree, tocItem.intervalAbsoluteMilliseconds)
      readingOrderIntervals[item.id] = tocItem.intervalAbsoluteMilliseconds
      readingOrderItemTree.add(tocItem.intervalAbsoluteMilliseconds)
      readingOrderItemsByInterval[tocItem.intervalAbsoluteMilliseconds] = item.id
    }

    return PlayerManifestTOC(
      tocItemsInOrder = tocItemsInOrder.toList(),
      tocItemsByInterval = tocItemsByInterval.toMap(),
      tocItemTree = tocItemTree,
      readingOrderIntervals = readingOrderIntervals,
      readingOrderItemTree = readingOrderItemTree,
      readingOrderItemsByInterval = readingOrderItemsByInterval
    )
  }

  private fun buildTOCItem(
    index: Int,
    item: PlayerManifestReadingOrderItem,
    offset: PlayerMillisecondsAbsolute,
    defaultTrackTitle: (Int) -> String
  ): PlayerManifestTOCItem {
    val title =
      titleOrDefault(item, defaultTrackTitle, index)

    val duration =
      Duration.standardSeconds((item.link.duration ?: 1L).toLong())
    val lower: PlayerMillisecondsAbsolute =
      offset
    val upper: PlayerMillisecondsAbsolute =
      PlayerMillisecondsAbsolute(offset.value + (Math.max(0, duration.millis - 1)))

    return PlayerManifestTOCItem(
      index = index,
      title = title,
      chapter = index + 1,
      intervalAbsoluteMilliseconds = PlayerMillisecondsAbsoluteInterval(lower, upper)
    )
  }

  private fun titleOrDefault(
    item: PlayerManifestReadingOrderItem,
    defaultTrackTitle: (Int) -> String,
    index: Int
  ): String {
    return if (!item.link.title.isNullOrBlank()) {
      item.link.title
    } else {
      defaultTrackTitle.invoke(index + 1)
    }
  }
}
