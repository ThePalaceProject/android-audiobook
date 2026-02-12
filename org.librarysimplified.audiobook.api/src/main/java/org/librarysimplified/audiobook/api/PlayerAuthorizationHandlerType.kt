package org.librarysimplified.audiobook.api

import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationType

/**
 * An authentication handler.
 *
 * The duty of an authentication handler is to configure HTTP requests with the appropriate
 * credentials.
 */

interface PlayerAuthorizationHandlerType {

  /**
   * If an authorization seems to have expired for requests of the given kind, then dismiss
   * any error that is still present. This method will
   * be called by parts of the code that attempt to perform downloads and discover that the
   * credentials are working.
   */

  fun onAuthorizationIsNoLongerInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  )

  /**
   * Authorization appears to have expired for requests of the given kind. This method will
   * be called by parts of the code that attempt to perform downloads and discover that the
   * credentials aren't valid (typically with an HTTP 401 error).
   */

  fun onAuthorizationIsInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  )

  /**
   * Produce an HTTP authorization value for a request made to fetch some part of a book. This
   * method will be called by parts of the code that are preparing to perform downloads.
   *
   * @param source The link to the data
   */

  fun onConfigureAuthorizationFor(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ): LSHTTPAuthorizationType?
}
