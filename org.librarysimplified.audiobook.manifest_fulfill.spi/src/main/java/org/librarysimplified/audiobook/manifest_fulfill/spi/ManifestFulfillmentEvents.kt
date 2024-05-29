package org.librarysimplified.audiobook.manifest_fulfill.spi

import org.librarysimplified.http.downloads.LSHTTPDownloadState
import org.librarysimplified.http.downloads.LSHTTPDownloadState.DownloadReceiving
import org.librarysimplified.http.downloads.LSHTTPDownloadState.DownloadStarted
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCancelled
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCompletedSuccessfully
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed

object ManifestFulfillmentEvents {

  fun ofDownloadState(
    state: LSHTTPDownloadState
  ): ManifestFulfillmentEvent {
    return when (state) {
      is DownloadReceiving -> {
        ManifestFulfillmentEvent(
          "Downloading: ${state.receivedSize} / ${state.expectedSize} (${state.bytesPerSecond} B/s)"
        )
      }

      DownloadStarted -> {
        ManifestFulfillmentEvent("Download started...")
      }

      DownloadCancelled -> {
        ManifestFulfillmentEvent("Download cancelled.")
      }

      is DownloadCompletedSuccessfully -> {
        ManifestFulfillmentEvent("Download completed successfully.")
      }

      is DownloadFailed.DownloadFailedExceptionally -> {
        ManifestFulfillmentEvent("Download failed: ${state.exception.message}")
      }

      is DownloadFailed.DownloadFailedServer -> {
        ManifestFulfillmentEvent("Download failed: ${state.responseStatus}")
      }

      is DownloadFailed.DownloadFailedUnacceptableMIME -> {
        ManifestFulfillmentEvent("Download failed: Unacceptable mime type.")
      }
    }
  }
}
