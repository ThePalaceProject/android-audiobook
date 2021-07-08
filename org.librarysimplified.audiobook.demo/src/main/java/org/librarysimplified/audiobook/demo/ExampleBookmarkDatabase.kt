package org.librarysimplified.audiobook.demo

import android.content.Context
import org.librarysimplified.audiobook.api.PlayerPosition
import org.slf4j.LoggerFactory
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * This object provides an excessively simple persistent database used to store the "last read"
 * positions of audio books.
 */

class ExampleBookmarkDatabase(private val context: Context) : AutoCloseable {

  private val logger =
    LoggerFactory.getLogger(ExampleBookmarkDatabase::class.java)

  private val ioExecutor =
    Executors.newSingleThreadExecutor { runnable ->
      val thread = Thread(runnable)
      thread.name = "org.librarysimplified.audiobook.demo.ExampleBookmarkDatabase[${thread.id}"
      thread
    }

  private val bookmarks: ConcurrentHashMap<String, SerializableBookmark> =
    this.loadMap()

  data class SerializableBookmark(
    val bookId: String,
    val title: String?,
    val part: Int,
    val chapter: Int,
    val offsetMilliseconds: Long
  ) : Serializable

  private fun countBookmarks(map: Map<String, SerializableBookmark>): Int {
    return map.size
  }

  fun bookmarkFindLastReadLocation(
    bookId: String
  ): PlayerPosition {
    return this.bookmarks[bookId]?.let { fromSerializable(it) } ?: PlayerPosition(null, 0, 0, 0L)
  }

  private fun fromSerializable(bookmark: SerializableBookmark): PlayerPosition {
    return PlayerPosition(
      title = bookmark.title,
      part = bookmark.part,
      chapter = bookmark.chapter,
      offsetMilliseconds = bookmark.offsetMilliseconds
    )
  }

  fun bookmarkDelete(
    bookId: String
  ) {
    this.logger.debug("deleting bookmark {}", bookId)

    val existing = this.bookmarks.remove(bookId)
    this.ioExecutor.execute { this.saveMap() }
  }

  fun bookmarkSave(
    bookId: String,
    bookmark: PlayerPosition
  ) {
    this.logger.debug("saving bookmark {}: {}", bookId, bookmark)
    this.bookmarks[bookId] = toSerializable(bookId, bookmark)
    this.ioExecutor.execute { this.saveMap() }
  }

  private fun toSerializable(
    bookId: String,
    bookmark: PlayerPosition
  ): SerializableBookmark {
    return SerializableBookmark(
      bookId = bookId,
      title = bookmark.title,
      part = bookmark.part,
      chapter = bookmark.chapter,
      offsetMilliseconds = bookmark.offsetMilliseconds
    )
  }

  private fun loadMap(): ConcurrentHashMap<String, SerializableBookmark> {
    return try {
      this.logger.debug("loading bookmarks")

      val file = File(this.context.filesDir, "bookmarks.dat")
      file.inputStream().use { stream ->
        ObjectInputStream(stream).use { objectStream ->
          val map = objectStream.readObject() as ConcurrentHashMap<String, SerializableBookmark>
          this.logger.debug("loaded {} bookmarks", this.countBookmarks(map))
          logBookmarks(map)
          map
        }
      }
    } catch (e: Exception) {
      this.logger.error("could not open bookmarks database: ", e)
      ConcurrentHashMap()
    }
  }

  private fun logBookmarks(map: ConcurrentHashMap<String, SerializableBookmark>) {
    for (entry in map.entries) {
      this.logger.debug("[{}]: bookmark {}", entry.key, entry.value)
    }
  }

  private fun saveMap() {
    try {
      this.logger.debug("saving {} bookmarks", this.countBookmarks(this.bookmarks))

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
}
