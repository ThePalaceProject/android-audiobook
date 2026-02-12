package org.librarysimplified.audiobook.demo

import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAUsernamePassword
import org.librarysimplified.http.api.LSHTTPAuthorizationBasic
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.slf4j.LoggerFactory

object ExampleAuthorizationHandler : PlayerAuthorizationHandlerType {

  private val logger =
    LoggerFactory.getLogger(ExampleAuthorizationHandler::class.java)

  @Volatile
  private var credentials: ExamplePlayerCredentials =
    ExamplePlayerCredentials.None(0)

  fun setCredentials(
    credentials: ExamplePlayerCredentials
  ) {
    this.logger.debug("Credentials set to {}", credentials)
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
        throw UnsupportedOperationException("Overdrive must use custom credentials.")
      }
    }
  }

  override fun <T : Any> onRequireCustomCredentialsFor(
    providerName: String,
    kind: PlayerDownloadRequest.Kind,
    credentialsType: Class<T>
  ): T {
    if (credentialsType == OPAUsernamePassword::class.java) {
      val current = this.credentials
      if (current is ExamplePlayerCredentials.Overdrive) {
        return credentialsType.cast(OPAUsernamePassword(
          current.userName,
          current.password
        ))
      }
    }
    throw UnsupportedOperationException("No available credentials of type $credentialsType")
  }
}
