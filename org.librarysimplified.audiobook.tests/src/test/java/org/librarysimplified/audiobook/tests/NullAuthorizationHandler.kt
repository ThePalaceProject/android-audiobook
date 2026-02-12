package org.librarysimplified.audiobook.tests

import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationType

class NullAuthorizationHandler : PlayerAuthorizationHandlerType {
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
    return null
  }
}
