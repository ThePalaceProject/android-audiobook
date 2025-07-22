package org.librarysimplified.audiobook.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem
import org.librarysimplified.audiobook.persistence.ADatabases
import java.io.IOException
import java.nio.file.Path
import java.util.Optional

class ADatabaseTest {

  @Test
  fun testNonexistentDirectory(
    @TempDir directory: Path
  ) {
    val nonexistent =
      directory.resolve("nonexistent")
    val db =
      nonexistent.resolve("database.db")

    assertThrows<IOException> { ADatabases.open(db) }
  }

  @Test
  fun testGetNonexistent(
    @TempDir directory: Path
  ) {
    val dbName =
      directory.resolve("database.db")
    val db =
      ADatabases.open(dbName)
    val bookID =
      PlayerBookID.transform("1d963e71-e855-4772-9f56-b717ec549ba4")

    db.use {
      assertEquals(Optional.empty<PlayerPosition>(), db.lastReadPositionGet(bookID))
    }
  }

  @Test
  fun testSetGet(
    @TempDir directory: Path
  ) {
    val dbName =
      directory.resolve("database.db")
    val db =
      ADatabases.open(dbName)
    val bookID0 =
      PlayerBookID.transform("1d963e71-e855-4772-9f56-b717ec549ba4")
    val bookID1 =
      PlayerBookID.transform("21df51d5-f2f3-4e58-be6a-ff9d9f946120")

    val position0 =
      PlayerPosition(
        PlayerManifestReadingOrderID("file0.ogg"),
        PlayerMillisecondsReadingOrderItem(1000L)
      )

    val position1 =
      PlayerPosition(
        PlayerManifestReadingOrderID("file0.ogg"),
        PlayerMillisecondsReadingOrderItem(2000L)
      )

    val position2 =
      PlayerPosition(
        PlayerManifestReadingOrderID("file1.ogg"),
        PlayerMillisecondsReadingOrderItem(1000L)
      )

    db.use {
      assertEquals(Optional.empty<PlayerPosition>(), db.lastReadPositionGet(bookID0))
      assertEquals(Optional.empty<PlayerPosition>(), db.lastReadPositionGet(bookID1))

      db.lastReadPositionSave(bookID0, position0)
      assertEquals(Optional.of<PlayerPosition>(position0), db.lastReadPositionGet(bookID0))
      assertEquals(Optional.empty<PlayerPosition>(), db.lastReadPositionGet(bookID1))

      db.lastReadPositionSave(bookID0, position1)
      assertEquals(Optional.of<PlayerPosition>(position1), db.lastReadPositionGet(bookID0))
      assertEquals(Optional.empty<PlayerPosition>(), db.lastReadPositionGet(bookID1))

      db.lastReadPositionSave(bookID0, position2)
      assertEquals(Optional.of<PlayerPosition>(position2), db.lastReadPositionGet(bookID0))
      assertEquals(Optional.empty<PlayerPosition>(), db.lastReadPositionGet(bookID1))

      db.lastReadPositionSave(bookID1, position0)
      assertEquals(Optional.of<PlayerPosition>(position2), db.lastReadPositionGet(bookID0))
      assertEquals(Optional.of<PlayerPosition>(position0), db.lastReadPositionGet(bookID1))
    }
  }
}
