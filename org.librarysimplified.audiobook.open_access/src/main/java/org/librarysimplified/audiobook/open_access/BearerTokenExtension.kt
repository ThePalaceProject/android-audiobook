package org.librarysimplified.audiobook.open_access

import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.api.PlayerDownloadRequestCredentials
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class BearerTokenExtension : PlayerExtensionType {
  private val logger =
    LoggerFactory.getLogger(BearerTokenExtension::class.java)

  override val name: String =
    "org.librarysimplified.audiobook.open_access.BearerTokenExtension"

  @Volatile
  var authorization: LSHTTPAuthorizationType? = null

  override fun onDownloadLink(
    statusExecutor: ExecutorService,
    downloadProvider: PlayerDownloadProviderType,
    originalRequest: PlayerDownloadRequest,
    link: PlayerManifestLink
  ): CompletableFuture<Unit>? {
    val authHeader = authorization?.toHeaderValue()

    return if (
      link.properties.encrypted?.scheme == null &&
      originalRequest.credentials == null &&
      authHeader != null &&
      authHeader.lowercase().startsWith("bearer ")
    ) {
      this.downloadWithBearerToken(
        downloadProvider = downloadProvider,
        originalRequest = originalRequest,
        token = authHeader.substring("bearer ".length)
      )
    } else {
      null
    }
  }

  private fun downloadWithBearerToken(
    downloadProvider: PlayerDownloadProviderType,
    originalRequest: PlayerDownloadRequest,
    token: String
  ): CompletableFuture<Unit> {
    this.logger.debug("running bearer token authentication for {}", originalRequest.uri)

    val newRequest =
      originalRequest.copy(credentials = PlayerDownloadRequestCredentials.BearerToken(token))

    return downloadProvider.download(newRequest)
  }
}
