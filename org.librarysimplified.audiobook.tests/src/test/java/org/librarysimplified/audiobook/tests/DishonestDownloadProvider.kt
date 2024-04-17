package org.librarysimplified.audiobook.tests

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListenableFutureTask
import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import java.util.concurrent.CompletableFuture

/**
 * An implementation of the {@link PlayerDownloadProviderType} interface that lies about
 * making progress and then claims success.
 */

class DishonestDownloadProvider : PlayerDownloadProviderType {

  override fun download(request: PlayerDownloadRequest): CompletableFuture<Unit> {
    return CompletableFuture.supplyAsync {
      request.onProgress.invoke(0)
      request.onProgress.invoke(50)
      request.onProgress.invoke(100)
    }
  }
}
