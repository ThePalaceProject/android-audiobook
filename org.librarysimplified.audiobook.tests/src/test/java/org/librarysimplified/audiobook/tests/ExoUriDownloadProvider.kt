package org.librarysimplified.audiobook.tests

import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import java.net.URI
import java.util.concurrent.CompletableFuture

/**
 * An implementation of the {@link PlayerDownloadProviderType} interface that receives a
 * method to call when the request is made and a map with the URIs that will be downloaded
 * and the number of times they were downloaded.
 */

class ExoUriDownloadProvider(
  private val onRequestSuccessfullyCompleted: (URI) -> Unit,
  private val uriDownloadTimes: HashMap<URI, Int>
) :
  PlayerDownloadProviderType {

  override fun download(request: PlayerDownloadRequest): CompletableFuture<Unit> {
    val numberOfTimesUriWasDownloaded = uriDownloadTimes[request.uri]

    // check if this URI is in the map and if it's not add it with
    if (numberOfTimesUriWasDownloaded == null) {
      uriDownloadTimes[request.uri] = 0

      // the URI is on the list, so we increment the number of times it was downloaded
    } else {
      uriDownloadTimes[request.uri] = numberOfTimesUriWasDownloaded + 1
    }

    onRequestSuccessfullyCompleted(request.uri)

    return CompletableFuture.supplyAsync {
      request.onProgress.invoke(0)
      request.onProgress.invoke(50)
      request.onProgress.invoke(100)
    }
  }
}
