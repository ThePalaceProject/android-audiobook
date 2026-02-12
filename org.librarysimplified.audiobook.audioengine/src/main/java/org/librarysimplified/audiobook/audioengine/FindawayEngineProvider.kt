package org.librarysimplified.audiobook.audioengine

import android.app.Application
import org.librarysimplified.audiobook.api.PlayerAudioBookProviderType
import org.librarysimplified.audiobook.api.PlayerAudioEngineProviderType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerVersion
import org.librarysimplified.audiobook.api.PlayerVersions
import org.slf4j.LoggerFactory

/**
 * An audio engine provider based on the Findaway API.
 *
 * Note: This class MUST have a no-argument public constructor in order to be used via
 * java.util.ServiceLoader.
 */

class FindawayEngineProvider : PlayerAudioEngineProviderType {

  private val version: PlayerVersion =
    PlayerVersions.ofPropertiesClassOrNull(
      clazz = FindawayEngineProvider::class.java,
      path = "/org/librarysimplified/audiobook/audioengine/provider.properties"
    ) ?: PlayerVersion(0, 0, 0)

  private val logger = LoggerFactory.getLogger(FindawayEngineProvider::class.java)

  override fun tryRequest(request: PlayerAudioEngineRequest): PlayerAudioBookProviderType? {
    val encrypted = request.manifest.metadata.encrypted
    if (encrypted == null) {
      this.logger.debug("cannot support a book without an 'encrypted' section")
      return null
    }

    if (encrypted.scheme != "http://librarysimplified.org/terms/drm/scheme/FAE") {
      this.logger.debug("cannot support a book with encryption scheme {}", encrypted.scheme)
      return null
    }

    return FindawayAudioBookProvider(request.manifest)
  }

  override fun tryDeleteRequest(
    context: Application,
    request: PlayerAudioEngineRequest
  ): Boolean {
    val provider = this.tryRequest(request)
    if (provider != null) {
      return provider.deleteBookData(
        context = context,
        authorizationHandler = request.authorizationHandler
      )
    }
    return false
  }

  override fun name(): String {
    return "org.librarysimplified.audiobook.audioengine"
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
