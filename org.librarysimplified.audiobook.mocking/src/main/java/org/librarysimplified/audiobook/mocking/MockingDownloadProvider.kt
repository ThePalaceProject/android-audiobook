package org.librarysimplified.audiobook.mocking

import com.google.common.util.concurrent.ListeningExecutorService
import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

/**
 * A fake download provider that takes URIs of the form "urn:n" where "n" is a positive integer,
 * and appears to download for "n" seconds.
 */

class MockingDownloadProvider(
  private val shouldFail: (PlayerDownloadRequest) -> Boolean,
  private val executorService: ListeningExecutorService
) : PlayerDownloadProviderType {

  override fun download(request: PlayerDownloadRequest): CompletableFuture<Unit> {
    val result = CompletableFuture<Unit>()

    this.reportProgress(request, 0)
    this.executorService.execute {
      try {
        if (this.shouldFail.invoke(request)) {
          throw IOException("Failed!")
        }
        result.complete(doDownload(request, result))
      } catch (e: CancellationException) {
        result.cancel(true)
      } catch (e: Throwable) {
        result.completeExceptionally(e)
      }
    }
    return result
  }

  private fun reportProgress(request: PlayerDownloadRequest, percent: Int) {
    try {
      request.onProgress(percent)
    } catch (e: Throwable) {
      e.printStackTrace()
    }
  }

  private fun doDownload(request: PlayerDownloadRequest, result: CompletableFuture<Unit>) {
    val time = Math.max(1, request.uri.rawSchemeSpecificPart.toInt()) * 10

    request.onProgress(0)
    for (i in 0..time) {
      if (result.isCancelled) {
        throw CancellationException()
      }

      val percent = ((i.toDouble() / time.toDouble()) * 100.0)
      this.reportProgress(request, percent.toInt())
      Thread.sleep(100L)
    }
    return
  }
}
