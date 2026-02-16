package org.librarysimplified.audiobook.api

import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationType

/**
 * A no-op authorization handler.
 */

object PlayerAuthorizationHandlerNoOp : PlayerAuthorizationHandlerType {

  override fun onAuthorizationIsNoLongerInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {
    // Don't care.
  }

  override fun onAuthorizationIsInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {
    // Don't care.
  }

  override fun onConfigureAuthorizationFor(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ): LSHTTPAuthorizationType? {
    return null
  }

  override fun <T : Any> onRequireCustomCredentialsFor(
    providerName: String,
    kind: PlayerDownloadRequest.Kind,
    credentialsType: Class<T>
  ): T {
    throw UnsupportedOperationException("No available credentials of type $credentialsType")
  }
}
