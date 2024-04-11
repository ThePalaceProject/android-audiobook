package org.librarysimplified.audiobook.media3

import org.librarysimplified.audiobook.api.PlayerAudioBookProviderType
import org.librarysimplified.audiobook.api.PlayerAudioEngineProviderType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerVersion
import org.librarysimplified.audiobook.api.PlayerVersions
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

const val LCP_SCHEME =
  "http://readium.org/2014/01/lcp"

/**
 * An audio engine provider based on ExoPlayer.
 *
 * Note: This class MUST have a no-argument public constructor in order to be used via
 * java.util.ServiceLoader.
 */

class ExoEngineProvider(
  private val threadFactory: (Runnable) -> ExoEngineThread
) : PlayerAudioEngineProviderType {

  constructor() : this({ runnable ->
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

  override fun tryRequest(
    request: PlayerAudioEngineRequest
  ): PlayerAudioBookProviderType? {
    val manifest = request.manifest
    val acceptedEncryptionSchemes =
      hashSetOf(
        "http://www.feedbooks.com/audiobooks/access-restriction",
        "https://www.feedbooks.com/audiobooks/access-restriction",
        LCP_SCHEME
      )

    if (manifest.readingOrder.any { item ->
        val link = item.link
        link.properties.encrypted != null &&
          !acceptedEncryptionSchemes.contains(link.properties.encrypted!!.scheme)
      }) {
      this.log.debug(
        "Cannot support a book in which any item in the reading order has encryption scheme not in [{}]",
        acceptedEncryptionSchemes.joinToString()
      )
      return null
    }

    val encrypted = manifest.metadata.encrypted
    if (encrypted != null) {
      if (encrypted.scheme == LCP_SCHEME) {
        if (request.file == null) {
          this.log.debug("Cannot support LCP books that have not been downloaded.")
          return null
        }
      } else {
        this.log.debug("Cannot support encrypted books with scheme {}.", encrypted.scheme)
        return null
      }
    }

    return ExoAudioBookProvider(
      request = request,
      engineExecutor = this.engineExecutor,
      manifest = manifest
    )
  }

  override fun name(): String {
    return "org.librarysimplified.audiobook.media3"
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
