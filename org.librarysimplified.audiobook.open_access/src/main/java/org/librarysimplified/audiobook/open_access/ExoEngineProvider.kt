package org.librarysimplified.audiobook.open_access

import org.librarysimplified.audiobook.api.PlayerAudioBookProviderType
import org.librarysimplified.audiobook.api.PlayerAudioEngineProviderType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerVersion
import org.librarysimplified.audiobook.api.PlayerVersions
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * An audio engine provider based on ExoPlayer.
 *
 * Note: This class MUST have a no-argument public constructor in order to be used via
 * java.util.ServiceLoader.
 */

class ExoEngineProvider(
  private val threadFactory: (Runnable) -> ExoEngineThread
) : PlayerAudioEngineProviderType {

  constructor() : this({
      runnable ->
    ExoEngineThread.create(runnable)
  })

  private val log = LoggerFactory.getLogger(ExoEngineProvider::class.java)

  private val version: PlayerVersion =
    PlayerVersions.ofPropertiesClassOrNull(
      clazz = ExoEngineProvider::class.java,
      path = "/org/librarysimplified/audiobook/rbdigital/provider.properties"
    ) ?: PlayerVersion(0, 0, 0)

  private val engineExecutor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor(this.threadFactory::invoke)

  override fun tryRequest(request: PlayerAudioEngineRequest): PlayerAudioBookProviderType? {
    val manifest = request.manifest
    val encrypted = manifest.metadata.encrypted
    if (encrypted != null) {
      this.log.debug("cannot support an encrypted book")
      return null
    }

    val acceptedEncryptionSchemes = hashSetOf(
      "http://www.feedbooks.com/audiobooks/access-restriction",
      "https://www.feedbooks.com/audiobooks/access-restriction"
    )

    if (manifest.readingOrder.any {
        it.properties.encrypted != null &&
          !acceptedEncryptionSchemes.contains(it.properties.encrypted!!.scheme)
      }) {
      this.log.debug("cannot support a book in which any item in the reading order has encryption scheme not in [{}]", acceptedEncryptionSchemes.joinToString())
      return null
    }

    return ExoAudioBookProvider(
      engineExecutor = this.engineExecutor,
      downloadProvider = request.downloadProvider,
      manifest = manifest,
      userAgent = request.userAgent
    )
  }

  override fun name(): String {
    return "org.librarysimplified.audiobook.open_access"
  }

  override fun version(): PlayerVersion {
    return this.version
  }

  override fun toString(): String {
    return StringBuilder(32)
      .append(this.name())
      .append(':')
      .append(this.version.major)
      .append('.')
      .append(this.version.minor)
      .append('.')
      .append(this.version.patch)
      .toString()
  }
}
