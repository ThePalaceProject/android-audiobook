package org.librarysimplified.audiobook.api

import org.librarysimplified.audiobook.api.extensions.PlayerAuthorizationHandlerExtensionType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.slf4j.LoggerFactory

/**
 * An authorization handler that is extensible with extensions and delegates to an
 * application-provided handler.
 */

class PlayerAuthorizationHandlerDelegating private constructor(
  val delegate: PlayerAuthorizationHandlerType,
  val extensions: List<PlayerAuthorizationHandlerExtensionType>
) : PlayerAuthorizationHandlerType {

  private val logger =
    LoggerFactory.getLogger(PlayerAuthorizationHandlerDelegating::class.java)

  companion object {
    fun create(
      delegate: PlayerAuthorizationHandlerType,
      extensions: List<PlayerAuthorizationHandlerExtensionType>
    ): PlayerAuthorizationHandlerType {
      return PlayerAuthorizationHandlerDelegating(delegate, extensions)
    }
  }

  override fun onAuthorizationIsNoLongerInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {
    this.delegate.onAuthorizationIsNoLongerInvalid(
      source = source,
      kind = kind
    )
  }

  override fun onAuthorizationIsInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {
    this.delegate.onAuthorizationIsInvalid(
      source = source,
      kind = kind
    )
  }

  override fun onConfigureAuthorizationFor(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ): LSHTTPAuthorizationType? {
    val original = this.delegate.onConfigureAuthorizationFor(source, kind)
    for (extension in this.extensions) {
      when (val override = extension.onOverrideAuthorizationFor(
        link = source,
        kind = kind,
        authorization = original
      )) {
        is PlayerAuthorizationHandlerExtensionType.OverrideError -> {
          this.logger.debug(
            "[{}]: Failed to override authorization for {}: {}",
            extension.name,
            kind,
            override.message
          )
        }

        PlayerAuthorizationHandlerExtensionType.OverrideNotApplicable -> {
          this.logger.debug(
            "[{}]: No applicable authorization override for {}",
            extension.name,
            kind
          )
        }

        is PlayerAuthorizationHandlerExtensionType.OverrideWith -> {
          this.logger.debug("[{}]: Overrode authorization for {}", extension.name, kind)
          return override.authorization
        }
      }
    }
    this.logger.debug("Did not override manifest authorization.")
    return original
  }

  override fun <T : Any> onRequireCustomCredentialsFor(
    providerName: String,
    kind: PlayerDownloadRequest.Kind,
    credentialsType: Class<T>
  ): T {
    return this.delegate.onRequireCustomCredentialsFor(providerName, kind, credentialsType)
  }
}
