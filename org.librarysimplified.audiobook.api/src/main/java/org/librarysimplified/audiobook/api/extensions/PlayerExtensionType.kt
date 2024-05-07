package org.librarysimplified.audiobook.api.extensions

import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

/**
 * The type of player extensions.
 *
 * Extensions are given the opportunity to replace specific parts of the functionality of existing
 * players. This is typically required by, for example, publishers requiring special types of
 * authentication to download chapters of books.
 */

interface PlayerExtensionType {

  /**
   * Provide (or revoke, if `null`) authorization information to this extension.
   */

  fun setAuthorization(authorization: LSHTTPAuthorizationType?)

  /**
   * The name of the extension. Conventionally, this is the fully-qualified name of the
   * extension class.
   */

  val name: String

  /**
   * Called when a chapter is about to be downloaded.
   *
   * This method gives extensions the opportunity to override calls made to the given download
   * provider in order to implement special behaviour such as proprietary authentication schemes.
   * If the extension does not require any special behaviour for downloads, this method MUST return
   * `null`.
   */

  fun onDownloadLink(
    statusExecutor: ExecutorService,
    downloadProvider: PlayerDownloadProviderType,
    originalRequest: PlayerDownloadRequest,
    link: PlayerManifestLink
  ): CompletableFuture<Unit>?
}
