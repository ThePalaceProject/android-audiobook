package org.librarysimplified.audiobook.api

import org.librarysimplified.audiobook.manifest.api.PlayerManifest

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
   * The source of the book data. This may be one of:
   *
   * * A book file, if the audiobook is a packaged book that has been downloaded (typically a zip file).
   * * A license file, if the audiobook is an LCP audiobook that will be streamed from a remote server.
   * * Nothing, if the audiobook is of a format that can work from a manifest alone.
   */

  val bookSource: PlayerBookSource?,

  /**
   * The credentials required to open the book.
   */

  val bookCredentials: PlayerBookCredentialsType
)
