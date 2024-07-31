package org.librarysimplified.audiobook.api

import android.app.Application
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType

/**
 * The interface exposed by audio engine providers.
 */

interface PlayerAudioEngineProviderType {

  /**
   * @return The name of the audio engine provider as a reverse-DNS style name
   */

  fun name(): String

  /**
   * @return The version of the audio engine provider
   */

  fun version(): PlayerVersion

  /**
   * Determine if the given manifest refers to a book that can be played by this audio engine
   * provider. If the book can be supported, the returned value allows for constructing the
   * actual book instance. If the book cannot be supported, the function returns `null`.
   *
   * @return An audio book provider, if the book is supported by the engine
   */

  fun tryRequest(request: PlayerAudioEngineRequest): PlayerAudioBookProviderType?

  /**
   * Run through the same steps as [tryRequest], but do not attempt to actually open an
   * audio engine for playback. Instead, instruct whichever audio engine can respond to the
   * request to delete any data it has for the book described by the request.
   *
   * @return `true` if book data was deleted by an engine
   */

  fun tryDeleteRequest(
    context: Application,
    extensions: List<PlayerExtensionType>,
    request: PlayerAudioEngineRequest
  ): Boolean
}
