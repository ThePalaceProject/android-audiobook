package org.librarysimplified.audiobook.api

import android.app.Application

/**
 * The interface exposed by audio book providers.
 */

interface PlayerAudioBookProviderType {

  /**
   * Create a new instance of an audio book.
   *
   * @param context An Android context
   * @param authorizationHandler The authorization handler
   */

  fun create(
    context: Application,
    authorizationHandler: PlayerAuthorizationHandlerType,
  ): PlayerResult<PlayerAudioBookType, Exception>

  /**
   * Delete any existing data for the audio book.
   *
   * @param context An Android context
   * @param authorizationHandler The authorization handler
   */

  fun deleteBookData(
    context: Application,
    authorizationHandler: PlayerAuthorizationHandlerType,
  ): Boolean
}
