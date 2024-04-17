package org.librarysimplified.audiobook.media3

import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerMissingTrackNameGeneratorType
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOC
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCs

/**
 * A manifest transformed such that it contains information relevant to the Exo audio engine.
 */

data class ExoManifest(
  val bookID: PlayerBookID,
  val originalManifest: PlayerManifest,
  val toc: PlayerManifestTOC,
  val readingOrderItems: List<ExoManifestMutableReadingOrderItem>
) {

  companion object {

    /**
     * Parse an ExoPlayer manifest from the given raw manifest.
     */

    fun transform(
      bookID: PlayerBookID,
      manifest: PlayerManifest,
      missingTrackNames: PlayerMissingTrackNameGeneratorType
    ): PlayerResult<ExoManifest, Exception> {
      try {
        val readingOrderItems =
          manifest.readingOrder.mapIndexed { index, item ->
            ExoManifestMutableReadingOrderItem(index, item)
          }

        return PlayerResult.Success(
          ExoManifest(
            bookID = bookID,
            originalManifest = manifest,
            toc = PlayerManifestTOCs.createTOC(
              manifest,
              defaultTrackTitle = { index -> missingTrackNames.generateName(index) }
            ),
            readingOrderItems = readingOrderItems
          )
        )
      } catch (e: Exception) {
        return PlayerResult.Failure(e)
      }
    }
  }
}
