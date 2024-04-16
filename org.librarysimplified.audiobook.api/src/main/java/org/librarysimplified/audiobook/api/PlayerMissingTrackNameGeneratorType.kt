package org.librarysimplified.audiobook.api

/**
 * A function used to generate track names when no titles are specified in manifests.
 */

interface PlayerMissingTrackNameGeneratorType {

  /**
   * @return A track name for the zero-indexed chapter index
   */

  fun generateName(trackIndex: Int): String
}
