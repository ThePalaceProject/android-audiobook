package org.librarysimplified.audiobook.api

import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.readium.r2.shared.publication.protection.ContentProtection
import java.io.File

/**
 * A request for an audio engine.
 */

data class PlayerAudioEngineRequest(
  /**
   * The book manifest.
   */

  val manifest: PlayerManifest,

  /**
   * The user agent used to make HTTP requests.
   */

  val userAgent: PlayerUserAgent,

  /**
   * A filter for audio engine providers. If the function returns `true`, then the engine provider
   * is included in the list of providers that can service the given request.
   */

  val filter: (PlayerAudioEngineProviderType) -> Boolean =
    {
      true
    },

  /**
   * A provider of downloads for book parts. Depending on the audio engine used, this provider
   * may not actually be used (some audio engines implement their own downloading and don't
   * allow for using a custom implementation).
   */

  val downloadProvider: PlayerDownloadProviderType,

  /**
   * The book file, if this is a packaged audio book that has been downloaded. This will be null
   * for unpackaged audio books.
   */

  val file: File? = null,

  /**
   * Content protections available to unlock the audio book, if it is protected.
   */

  val contentProtections: List<ContentProtection> = listOf(),

  /**
   * A passphrase, if needed, will be manually inserted.
   */
  val manualPassphrase: Boolean = false
)
