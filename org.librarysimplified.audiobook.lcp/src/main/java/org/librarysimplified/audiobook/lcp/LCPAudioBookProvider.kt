package org.librarysimplified.audiobook.lcp

import android.content.Context
import org.librarysimplified.audiobook.api.PlayerAudioBookProviderType
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerResult.Failure
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.open_access.ExoManifest
import org.readium.r2.shared.publication.ContentProtection
import java.io.File
import java.util.concurrent.ScheduledExecutorService

/**
 * The LCP implementation of the {@link PlayerAudioBookProviderType} interface.
 */

class LCPAudioBookProvider(
  private val engineExecutor: ScheduledExecutorService,
  private val manifest: PlayerManifest,
  private val file: File,
  private val contentProtections: List<ContentProtection>,
  private val manualPassphrase: Boolean
) : PlayerAudioBookProviderType {

  override fun create(
    context: Context,
    extensions: List<PlayerExtensionType>
  ): PlayerResult<PlayerAudioBookType, Exception> {
    try {
      return when (val parsed = ExoManifest.transform(context, this.manifest)) {
        is PlayerResult.Success ->
          PlayerResult.Success(
            LCPAudioBook.create(
              context = context,
              engineExecutor = this.engineExecutor,
              manifest = parsed.result,
              file = this.file,
              contentProtections = this.contentProtections,
              manualPassphrase = manualPassphrase
            )
          )
        is Failure ->
          Failure(parsed.failure)
      }
    } catch (e: Exception) {
      return Failure(e)
    }
  }
}
