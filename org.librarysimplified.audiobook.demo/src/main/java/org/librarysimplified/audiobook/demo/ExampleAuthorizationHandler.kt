package org.librarysimplified.audiobook.demo

import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationBasic
import org.librarysimplified.http.api.LSHTTPAuthorizationType

object ExampleAuthorizationHandler : PlayerAuthorizationHandlerType {

  @Volatile
  private var credentials: ExamplePlayerCredentials =
    ExamplePlayerCredentials.None(0)

  fun setCredentials(
    credentials: ExamplePlayerCredentials
  ) {
    this.credentials = credentials
  }

  override fun onAuthorizationIsNoLongerInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {
    // Nothing yet
  }

  override fun onAuthorizationIsInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {
    // Nothing yet.
  }

  override fun onConfigureAuthorizationFor(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ): LSHTTPAuthorizationType? {
    return when (val c = this.credentials) {
      is ExamplePlayerCredentials.Basic -> {
        LSHTTPAuthorizationBasic.ofUsernamePassword(
          userName = c.userName,
          password = c.password
        )
      }

      is ExamplePlayerCredentials.Feedbooks -> {
        LSHTTPAuthorizationBasic.ofUsernamePassword(
          userName = c.userName,
          password = c.password
        )
      }

      is ExamplePlayerCredentials.None -> {
        null
      }

      is ExamplePlayerCredentials.Overdrive -> {
        null
      }
    }
  }
}
