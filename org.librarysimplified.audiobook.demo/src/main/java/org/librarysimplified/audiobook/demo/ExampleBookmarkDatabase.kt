package org.librarysimplified.audiobook.demo

import android.app.Application
import org.joda.time.DateTime
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerBookmarkKind
import org.librarysimplified.audiobook.api.PlayerBookmarkMetadata
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.slf4j.LoggerFactory
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.concurrent.Executors

/**
 * This object provides an excessively simple persistent database used to store bookmarks
 * for audio books. This is simply for the sake of example, and should not be used in production.
 */

class ExampleBookmarkDatabase(private val context: Application) : AutoCloseable {

  private val logger =
    LoggerFactory.getLogger(ExampleBookmarkDatabase::class.java)

  private val ioExecutor =
    Executors.newSingleThreadExecutor { runnable ->
      val thread = Thread(runnable)
      thread.name = "org.librarysimplified.audiobook.demo.ExampleBookmarkDatabase[${thread.id}]"
      thread
    }

  private val bookmarks: SerializableBookmarkCollection =
    this.loadCollection()

  private data class SerializableBookID(
    val value: String
  ) : Serializable

  private data class SerializableReadingOrderID(
    val value: String
  ) : Serializable

  private data class SerializablePosition(
    val id: SerializableReadingOrderID,
    val offset: Long
  ) : Serializable

  private data class SerializableMetadata(
    val chapterTitle: String,
    val chapterProgressEstimate: Double,
    val creationTime: DateTime,
    val bookProgressEstimate: Double,
    val totalRemainingBookTime: Duration
  ) : Serializable {
    fun toMetadata(): PlayerBookmarkMetadata {
      return PlayerBookmarkMetadata(
        chapterTitle = this.chapterTitle,
        chapterProgressEstimate = this.chapterProgressEstimate,
        creationTime = this.creationTime,
        bookProgressEstimate = this.bookProgressEstimate,
        totalRemainingBookTime = this.totalRemainingBookTime
      )
    }
  }

  private data class SerializableBookmark(
    val kind: PlayerBookmarkKind,
    val bookId: SerializableBookID,
    val position: SerializablePosition,
    val metadata: SerializableMetadata
  ) : Serializable {
    fun toBookmark(): PlayerBookmark {
      return PlayerBookmark(
        kind = this.kind,
        readingOrderID = PlayerManifestReadingOrderID(this.position.id.value),
        offsetMilliseconds = this.position.offset,
        metadata = this.metadata.toMetadata()
      )
    }
  }

  private data class SerializableBookmarkCollection(
    val bookmarksExplicit: MutableMap<SerializableBookID, MutableMap<SerializablePosition, SerializableBookmark>>,
    val bookmarksLastRead: MutableMap<SerializableBookID, SerializableBookmark>
  ) : Serializable

  private fun countBookmarks(bookmarkCollection: SerializableBookmarkCollection): Int {
    var count = 0
    for (entry in bookmarkCollection.bookmarksExplicit) {
      for (bookEntry in entry.value.entries) {
        ++count
      }
    }
    for (entry in bookmarkCollection.bookmarksLastRead) {
      ++count
    }
    return count
  }

  fun bookmarkListExplicits(
    bookId: String
  ): List<PlayerBookmark> {
    val serializableBookID =
      SerializableBookID(bookId)
    val explicitsForBook =
      this.bookmarks.bookmarksExplicit[serializableBookID] ?: mapOf()

    return explicitsForBook.values.toList()
      .sortedBy { b -> b.metadata.creationTime }
      .map(SerializableBookmark::toBookmark)
  }

  fun bookmarkFindLastRead(
    bookId: String
  ): PlayerBookmark? {
    return this.bookmarks.bookmarksLastRead[SerializableBookID(bookId)]?.toBookmark()
  }

  fun bookmarkDelete(
    bookId: String,
    bookmark: PlayerBookmark
  ) {
    this.logger.debug("Deleting bookmark {} {}", bookId, bookmark)
    this.bookmarks.bookmarksExplicit[SerializableBookID(bookId)]
      ?.remove(
        SerializablePosition(
          SerializableReadingOrderID(bookmark.readingOrderID.text),
          bookmark.offsetMilliseconds
        )
      )
    this.ioExecutor.execute { this.saveMap() }
  }

  private fun bookmarkSaveExplicit(
    bookId: String,
    bookmark: PlayerBookmark
  ) {
    this.logger.debug("Saving explicit bookmark {}: {}", bookId, bookmark)

    val position =
      SerializablePosition(
        id = SerializableReadingOrderID(bookmark.readingOrderID.text),
        offset = bookmark.offsetMilliseconds
      )

    val serializableBookID =
      SerializableBookID(bookId)
    val explicitsForBook =
      this.bookmarks.bookmarksExplicit[serializableBookID] ?: mutableMapOf()
    explicitsForBook[position] = toSerializable(bookId, bookmark)
    this.bookmarks.bookmarksExplicit[serializableBookID] = explicitsForBook

    this.ioExecutor.execute { this.saveMap() }
  }

  private fun bookmarkSaveLastRead(
    bookId: String,
    bookmark: PlayerBookmark
  ) {
    this.logger.debug("Saving last-read bookmark {}: {}", bookId, bookmark)

    val serializableBookID = SerializableBookID(bookId)
    this.bookmarks.bookmarksLastRead[serializableBookID] = toSerializable(bookId, bookmark)
    this.ioExecutor.execute { this.saveMap() }
  }

  fun bookmarkSave(
    bookId: String,
    bookmark: PlayerBookmark
  ) {
    when (bookmark.kind) {
      PlayerBookmarkKind.EXPLICIT -> this.bookmarkSaveExplicit(bookId, bookmark)
      PlayerBookmarkKind.LAST_READ -> this.bookmarkSaveLastRead(bookId, bookmark)
    }
  }

  private fun toSerializable(
    bookId: String,
    bookmark: PlayerBookmark
  ): SerializableBookmark {
    return SerializableBookmark(
      kind = bookmark.kind,
      bookId = SerializableBookID(bookId),
      position = SerializablePosition(
        id = SerializableReadingOrderID(bookmark.readingOrderID.text),
        offset = bookmark.offsetMilliseconds
      ),
      metadata = SerializableMetadata(
        chapterTitle = bookmark.metadata.chapterTitle,
        chapterProgressEstimate = bookmark.metadata.chapterProgressEstimate,
        creationTime = bookmark.metadata.creationTime,
        bookProgressEstimate = bookmark.metadata.bookProgressEstimate,
        totalRemainingBookTime = bookmark.metadata.totalRemainingBookTime
      )
    )
  }

  private fun loadCollection(): SerializableBookmarkCollection {
    return try {
      this.logger.debug("Loading bookmarks")

      val file = File(this.context.filesDir, "bookmarks.dat")
      file.inputStream().use { stream ->
        ObjectInputStream(stream).use { objectStream ->
          val map = objectStream.readObject() as SerializableBookmarkCollection
          this.logger.debug("Loaded {} bookmarks", this.countBookmarks(map))
          this.logBookmarks(map)
          map
        }
      }
    } catch (e: Exception) {
      this.logger.error("Could not open bookmarks database: ", e)
      SerializableBookmarkCollection(
        bookmarksExplicit = mutableMapOf(),
        bookmarksLastRead = mutableMapOf()
      )
    }
  }

  private fun logBookmarks(bookmarkCollection: SerializableBookmarkCollection) {
    for (entry in bookmarkCollection.bookmarksExplicit) {
      val bookId = entry.key
      for (bookEntry in entry.value.entries) {
        val bookmark = bookEntry.value
        this.logger.debug("[{}]: Bookmark Explicit {}", bookId, bookmark)
      }
    }
    for (entry in bookmarkCollection.bookmarksLastRead) {
      val bookId = entry.key
      val bookmark = entry.value
      this.logger.debug("[{}]: Bookmark Last-Read {}", bookId, bookmark)
    }
  }

  private fun saveMap() {
    try {
      this.logger.debug("Saving {} bookmarks", this.countBookmarks(this.bookmarks))

      val fileTmp =
        File(this.context.filesDir, "bookmarks.dat.tmp")
      val file =
        File(this.context.filesDir, "bookmarks.dat")

      fileTmp.outputStream().use { stream ->
        ObjectOutputStream(stream).use { objectStream ->
          objectStream.writeObject(this.bookmarks)
        }
      }
      fileTmp.renameTo(file)
    } catch (e: Exception) {
      this.logger.error("could not save bookmarks database: ", e)
    }
  }

  override fun close() {
    this.ioExecutor.shutdown()
  }

  fun bookmarkList(bookId: String): List<PlayerBookmark> {
    val results = mutableListOf<PlayerBookmark>()
    this.bookmarkFindLastRead(bookId)?.let { bookmark -> results.add(bookmark) }
    results.addAll(this.bookmarkListExplicits(bookId))
    return results.toList()
  }
}
