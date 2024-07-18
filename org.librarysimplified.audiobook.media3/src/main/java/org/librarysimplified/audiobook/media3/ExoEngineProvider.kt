package org.librarysimplified.audiobook.media3

import org.librarysimplified.audiobook.api.PlayerAudioBookProviderType
import org.librarysimplified.audiobook.api.PlayerAudioEngineProviderType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerBookSource
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

  companion object {
    val LCP_SCHEME =
      "http://readium.org/2014/01/lcp"

    val FEEDBOOKS_SCHEME_0 =
      "http://www.feedbooks.com/audiobooks/access-restriction"

    val FEEDBOOKS_SCHEME_1 =
      "https://www.feedbooks.com/audiobooks/access-restriction"

    val ACCEPTED_SCHEMES =
      setOf(LCP_SCHEME, FEEDBOOKS_SCHEME_0, FEEDBOOKS_SCHEME_1)
  }

  constructor() : this({ runnable -> ExoEngineThread.create(runnable) })

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
    for (item in manifest.readingOrder) {
      val link = item.link
      val encrypted = link.properties.encrypted
      if (encrypted != null) {
        val scheme = encrypted.scheme
        if (!ACCEPTED_SCHEMES.contains(scheme)) {
          this.log.debug(
            "Reading order item contains encryption scheme {}, which is not in the supported set {}",
            scheme,
            ACCEPTED_SCHEMES
          )
          return null
        }
      }
    }

    val encrypted = manifest.metadata.encrypted
    if (encrypted != null) {
      val scheme = encrypted.scheme
      if (!ACCEPTED_SCHEMES.contains(scheme)) {
        this.log.debug(
          "Book is encrypted with scheme {}, which is not in the supported set {}",
          encrypted.scheme,
          ACCEPTED_SCHEMES
        )
        return null
      }
    }

    if (ExoLCP.isLCP(manifest)) {
      when (request.bookSource) {
        is PlayerBookSource.PlayerBookSourceFile,
        is PlayerBookSource.PlayerBookSourceLicenseFile -> {
          // At least one of these is required.
        }
        null -> {
          this.log.debug("LCP audiobooks must either have a book file, or a license file.")
        }
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
