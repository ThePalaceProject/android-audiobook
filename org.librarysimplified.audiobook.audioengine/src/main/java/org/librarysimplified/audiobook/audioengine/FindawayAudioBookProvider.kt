package org.librarysimplified.audiobook.audioengine

import android.app.Application
import org.librarysimplified.audiobook.api.PlayerAudioBookProviderType
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.slf4j.LoggerFactory

/**
 * The Findaway implementation of the {@link PlayerAudioBookProviderType} interface.
 */

class FindawayAudioBookProvider(
  private val manifest: PlayerManifest
) : PlayerAudioBookProviderType {

  private val logger =
    LoggerFactory.getLogger(FindawayAudioBookProvider::class.java)

  override fun create(
    context: Application,
    authorizationHandler: PlayerAuthorizationHandlerType
  ): PlayerResult<PlayerAudioBookType, Exception> {
    val id =
      PlayerBookID.transform(this.manifest.metadata.identifier)

    return when (
      val parsed = FindawayManifest.transform(
        context = context,
        bookID = id,
        manifest = this.manifest
      )
    ) {
      is PlayerResult.Success ->
        try {
          PlayerResult.Success(
            FindawayAudioBook.create(
              context = context,
              manifest = parsed.result
            )
          )
        } catch (e: FindawayInitializationException) {
          PlayerResult.Failure(e)
        }

      is PlayerResult.Failure ->
        PlayerResult.Failure(parsed.failure)
    }
  }

  override fun deleteBookData(
    context: Application,
    authorizationHandler: PlayerAuthorizationHandlerType,
  ): Boolean {
    this.logger.debug("deleteBookData")
    return when (val book = create(context, authorizationHandler)) {
      is PlayerResult.Failure -> {
        false
      }
      is PlayerResult.Success -> {
        book.result.wholeBookDownloadTask.delete()
        return true
      }
    }
  }
}
