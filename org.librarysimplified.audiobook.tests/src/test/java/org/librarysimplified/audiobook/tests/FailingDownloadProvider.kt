package org.librarysimplified.audiobook.tests

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import java.text.ParseException
import java.util.concurrent.CompletableFuture

/**
 * An implementation of the {@link PlayerDownloadProviderType} interface that fails all
 * downloads.
 */

class FailingDownloadProvider : PlayerDownloadProviderType {

  override fun download(request: PlayerDownloadRequest): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    future.completeExceptionally(ParseException("Error!", 0))
    return future
  }
}
