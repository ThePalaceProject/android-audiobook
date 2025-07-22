package org.librarysimplified.audiobook.persistence

import org.librarysimplified.audiobook.persistence.internal.ADatabase
import java.io.IOException
import java.nio.file.Path

/**
 * A database based on SQLite.
 */

object ADatabases : ADatabaseFactoryType {
  @Throws(IOException::class)
  override fun open(
    file: Path
  ): ADatabaseType {
    return ADatabase.open(file)
  }
}
