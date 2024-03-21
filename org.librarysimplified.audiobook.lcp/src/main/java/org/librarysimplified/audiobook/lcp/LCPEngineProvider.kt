package org.librarysimplified.audiobook.lcp

import org.librarysimplified.audiobook.api.PlayerAudioBookProviderType
import org.librarysimplified.audiobook.api.PlayerAudioEngineProviderType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerVersion
import org.librarysimplified.audiobook.api.PlayerVersions
import org.librarysimplified.audiobook.open_access.ExoEngineThread
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * An audio engine provider for LCP-encrypted books, based on ExoPlayer.
 *
 * Note: This class MUST have a no-argument public constructor in order to be used via
 * java.util.ServiceLoader.
 */

class LCPEngineProvider(
  private val threadFactory: (Runnable) -> ExoEngineThread
) : PlayerAudioEngineProviderType {

  constructor() : this({
      runnable ->
    ExoEngineThread.create(runnable)
  })

  private val log = LoggerFactory.getLogger(LCPEngineProvider::class.java)

  private val version: PlayerVersion =
    PlayerVersions.ofPropertiesClassOrNull(
      clazz = LCPEngineProvider::class.java,
      path = "/org/librarysimplified/audiobook/lcp/provider.properties"
    ) ?: PlayerVersion(0, 0, 0)

  private val engineExecutor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor(this.threadFactory::invoke)

  override fun tryRequest(request: PlayerAudioEngineRequest): PlayerAudioBookProviderType? {
    val manifest = request.manifest
    val expectedEncryptionScheme = "http://readium.org/2014/01/lcp"

    if (manifest.readingOrder.any { item ->
        item.link.properties.encrypted?.scheme != expectedEncryptionScheme
      }) {
      this.log.debug(
        "cannot support a book in which any item in the reading order does not have encryption scheme {}",
        expectedEncryptionScheme
      )

      return null
    }

    if (request.file == null) {
      this.log.debug("cannot support an LCP audio book that has not been downloaded")

      return null
    }

    return LCPAudioBookProvider(
      engineExecutor = this.engineExecutor,
      manifest = manifest,
      file = request.file!!,
      contentProtections = request.contentProtections ?: listOf(),
      manualPassphrase = request.manualPassphrase
    )
  }

  override fun name(): String {
    return "org.librarysimplified.audiobook.lcp"
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
