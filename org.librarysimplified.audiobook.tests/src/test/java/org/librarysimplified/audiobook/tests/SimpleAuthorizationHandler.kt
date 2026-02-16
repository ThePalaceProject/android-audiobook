package org.librarysimplified.audiobook.tests

import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationType

class SimpleAuthorizationHandler : PlayerAuthorizationHandlerType {

  var authorization: LSHTTPAuthorizationType? = null

  override fun onAuthorizationIsNoLongerInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {

  }

  override fun onAuthorizationIsInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {
  }

  override fun onConfigureAuthorizationFor(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ): LSHTTPAuthorizationType? {
    return this.authorization
  }

  override fun <T : Any> onRequireCustomCredentialsFor(
    providerName: String,
    kind: PlayerDownloadRequest.Kind,
    credentialsType: Class<T>
  ): T {
    throw UnsupportedOperationException("No available credentials of type $credentialsType")
  }
}
