package org.librarysimplified.audiobook.api

/**
 * An identifier for a book that was taken from the feed used to deliver the book. There are
 * no restrictions on format, and the identifier is not guaranteed to be globally unique.
 */

data class PlayerOPDSID(val value: String) {
  override fun toString(): String {
    return this.value
  }
}
