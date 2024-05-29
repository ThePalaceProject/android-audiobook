package org.librarysimplified.audiobook.manifest_fulfill.spi

import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedExceptionally
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedServer
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedUnacceptableMIME
import java.net.URI

object ManifestFulfillmentErrors {

  private data class LocalError(
    override val message: String
  ) : ManifestFulfillmentErrorType {
    override val serverData: ManifestFulfillmentErrorType.ServerData? =
      null
  }

  fun ofDownloadResult(
    url: URI,
    result: LSHTTPDownloadResult.DownloadFailed
  ): ManifestFulfillmentErrorType {
    return when (result) {
      is DownloadFailedExceptionally -> {
        LocalError("Download failed (${result.exception.javaClass}: ${result.exception.message})")
      }

      is DownloadFailedServer -> {
        val response =
          result.responseStatus
        val responseCode =
          response.properties.status
        val responseMessage =
          response.properties.message
        val contentType =
          response.properties.contentType.toString()

        val status = result.responseStatus
        ManifestFulfillmentErrorHTTPRequestFailed(
          message = responseMessage,
          serverData = ManifestFulfillmentErrorType.ServerData(
            uri = url,
            code = responseCode,
            receivedBody = status.bodyStream?.readBytes() ?: ByteArray(0),
            receivedContentType = contentType
          )
        )
      }

      is DownloadFailedUnacceptableMIME -> {
        LocalError("Unacceptable MIME type.")
      }
    }
  }
}
