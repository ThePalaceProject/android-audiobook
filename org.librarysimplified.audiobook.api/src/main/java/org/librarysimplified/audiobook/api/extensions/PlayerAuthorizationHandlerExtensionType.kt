package org.librarysimplified.audiobook.api.extensions

import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationType

/**
 * The type of player authentication handler extensions.
 */

interface PlayerAuthorizationHandlerExtensionType {

  /**
   * The name of the extension. Conventionally, this is the fully-qualified name of the
   * extension class.
   */

  val name: String

  sealed interface AuthenticationOverrideType

  data class OverrideWith(
    val authorization: LSHTTPAuthorizationType?
  ) : AuthenticationOverrideType

  data object OverrideNotApplicable : AuthenticationOverrideType

  data class OverrideError(
    val message: String
  ) : AuthenticationOverrideType

  /**
   * Allow for overriding the HTTP authorization returned from the authentication handler,
   * before part of a book is downloaded.
   */

  fun onOverrideAuthorizationFor(
    link: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind,
    authorization: LSHTTPAuthorizationType?
  ): AuthenticationOverrideType
}
