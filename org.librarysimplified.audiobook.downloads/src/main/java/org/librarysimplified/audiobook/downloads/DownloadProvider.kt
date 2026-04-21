package org.librarysimplified.audiobook.downloads

import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.librarysimplified.http.api.LSHTTPNetworkAccessReadableType.LSHTTPNetworkAvailability.NETWORK_AVAILABLE
import org.librarysimplified.http.api.LSHTTPNetworkAccessReadableType.LSHTTPNetworkAvailability.NETWORK_NOT_PERMITTED
import org.librarysimplified.http.api.LSHTTPNetworkAccessReadableType.LSHTTPNetworkAvailability.NETWORK_UNAVAILABLE
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.slf4j.LoggerFactory
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/*
 * A simple download provider.
 */

class DownloadProvider private constructor(
  private val requests: LinkedBlockingQueue<Request>
) : PlayerDownloadProviderType {

  private val log =
    LoggerFactory.getLogger(DownloadProvider::class.java)

  private val closed =
    AtomicBoolean(false)
  private val cancelling =
    AtomicBoolean(false)

  companion object {

    /**
     * Create a new download provider.
     *
     * @param executor A listening executor that will be used for download tasks
     */

    fun create(
      executor: ExecutorService
    ): PlayerDownloadProviderType {
      val provider = DownloadProvider(LinkedBlockingQueue())
      executor.execute(provider::start)
      return provider
    }
  }

  private data class Request(
    val downloadRequest: PlayerDownloadRequest,
    val future: CompletableFuture<Unit>
  )

  private fun start() {
    while (!this.closed.get()) {
      try {
        val request = this.requests.poll(1L, TimeUnit.SECONDS)
        if (request != null) {
          this.executeRequest(request)
        }
      } catch (e: Throwable) {
        this.log.debug("Download request processing error: ", e)
      }
    }
  }

  private fun executeRequest(
    request: Request
  ) {
    this.reportProgress(request.downloadRequest, 0)

    try {
      request.future.complete(this.doDownload(request.downloadRequest, request.future))
    } catch (_: CancellationException) {
      this.doCleanUp(request.downloadRequest)
      request.future.cancel(true)
    } catch (e: Throwable) {
      request.future.completeExceptionally(e)
      this.doCleanUp(request.downloadRequest)
    }
  }

  override fun download(
    request: PlayerDownloadRequest
  ): CompletableFuture<Unit> {
    val result = CompletableFuture<Unit>()
    return if (!this.cancelling.get()) {
      this.reportProgress(request, 0)
      this.requests.add(Request(request, result))
      result
    } else {
      result.cancel(true)
      result
    }
  }

  override fun cancelAll() {
    if (this.cancelling.compareAndSet(false, true)) {
      try {
        for (request in this.requests) {
          try {
            request.future.cancel(true)
          } catch (e: Throwable) {
            this.log.debug("Failed to cancel download task: ", e)
          }
        }
        this.requests.clear()
      } catch (e: Throwable) {
        this.log.debug("Failed to cancel download tasks: ", e)
      } finally {
        this.cancelling.set(false)
      }
    }
  }

  private fun reportProgress(
    request: PlayerDownloadRequest,
    percent: Int
  ) {
    try {
      request.onProgress(percent)
    } catch (e: Throwable) {
      this.log.error("Ignored onProgress exception: ", e)
    }
  }

  private fun doCleanUp(request: PlayerDownloadRequest) {
    this.log.debug("Cleaning up output file {}", request.outputFile)
    DownloadFileIO.fileDelete(request.outputFile)
  }

  private fun doDownload(
    request: PlayerDownloadRequest,
    result: CompletableFuture<Unit>
  ) {
    this.log.debug("Downloading {} to {}", request.link.hrefURI, request.outputFile)

    this.reportProgress(request, 0)

    val link = request.link
    if (link !is PlayerManifestLink.LinkBasic) {
      this.log.debug("Templated links are not supported.")
      throw IOException("Templated links are not supported.")
    }
    val targetURI = link.hrefURI
    if (targetURI == null) {
      this.log.debug("Links without href values are not supported.")
      throw IOException("Links without href values are not supported.")
    }

    when (request.httpClient.networkAccess.canUseNetwork()) {
      NETWORK_UNAVAILABLE -> {
        throw IOException("The network is currently unavailable.")
      }

      NETWORK_AVAILABLE -> {
        this.log.debug("Downloads are permitted by network access.")
      }

      NETWORK_NOT_PERMITTED -> {
        throw IOException("Downloads are not permitted by the current network settings.")
      }
    }

    val httpRequestBuilder =
      request.httpClient.newRequest(targetURI)

    this.configureRequestCredentials(request, httpRequestBuilder)
    val httpRequest = httpRequestBuilder.build()

    this.log.debug("Executing HTTP request.")
    val response = httpRequest.execute()

    when (val status = response.status) {
      is LSHTTPResponseStatus.Failed -> {
        throw status.exception
      }

      is LSHTTPResponseStatus.Responded.Error -> {
        if (status.properties.status == 401) {
          request.authorizationHandler.onAuthorizationIsInvalid(
            source = request.link,
            kind = request.kind
          )
        } else {
          request.authorizationHandler.onAuthorizationIsNoLongerInvalid(
            source = request.link,
            kind = request.kind
          )
        }

        throw IOException(
          StringBuilder(128)
            .append("Server returned an error response.\n")
            .append("  Response: ")
            .append(response.properties?.status)
            .append(' ')
            .append(response.properties?.message)
            .append('\n')
            .toString()
        )
      }

      is LSHTTPResponseStatus.Responded.OK -> {
        this.handleSuccessfulResponse(status, request, result)
      }
    }
  }

  private fun configureRequestCredentials(
    request: PlayerDownloadRequest,
    httpRequestBuilder: LSHTTPRequestBuilderType
  ) {
    val auth: LSHTTPAuthorizationType? =
      request.authorizationHandler.onConfigureAuthorizationFor(
        source = request.link,
        kind = request.kind
      )

    if (auth == null) {
      this.log.debug("Not using authentication for {} {}", request.kind, request.link.hrefURI)
      return
    }

    httpRequestBuilder.addHeader("Authorization", auth.toHeaderValue())
  }

  private fun handleSuccessfulResponse(
    response: LSHTTPResponseStatus.Responded.OK,
    request: PlayerDownloadRequest,
    result: CompletableFuture<Unit>
  ) {
    /*
     * Check if the future has been cancelled. If it has, don't start copying.
     */

    if (result.isCancelled) {
      this.log.debug("Download cancelled")
      throw CancellationException()
    }

    val body =
      response.bodyStream ?: throw IOException("HTTP server response did not contain a body")
    val expectedLength =
      response.properties.contentLength

    /*
     * Try to create the parent directory (and all of the required ancestors too). Ignore
     * errors, because the actual error will occur when an attempt is made to open the file.
     */

    request.outputFile.parentFile?.mkdirs()

    body.use { inputStream ->
      FileOutputStream(request.outputFileTemp, false).use { outputStream ->
        this.copyStream(request, inputStream, outputStream, expectedLength, result)
        request.outputFileTemp.renameTo(request.outputFile)
      }
    }

    if (expectedLength != null) {
      val receivedSize = request.outputFile.length()
      if (receivedSize != expectedLength) {
        throw IOException(
          StringBuilder(128)
            .append("Resulting file size does not match the expected size.\n")
            .append("  Expected size: ")
            .append(expectedLength)
            .append('\n')
            .append("  Received size: ")
            .append(receivedSize)
            .append('\n')
            .toString()
        )
      }
    }

    request.onCompletion.invoke(request.outputFile)
  }

  private fun copyStream(
    request: PlayerDownloadRequest,
    inputStream: InputStream,
    outputStream: FileOutputStream,
    expectedLength: Long?,
    result: CompletableFuture<Unit>
  ) {
    var progressCurrent: Double
    var received = 0L
    val buffer = ByteArray(1024)
    var timeLast = System.currentTimeMillis()

    this.reportProgress(request, 0)

    while (true) {
      /*
       * Check if the future has been cancelled. If it has, stop copying.
       */

      if (result.isCancelled) {
        this.log.debug("Download cancelled")
        throw CancellationException()
      }

      val r = inputStream.read(buffer)
      if (r == -1) {
        break
      }
      received += r
      outputStream.write(buffer, 0, r)

      /*
       * Throttle progress updates to one every ~1000ms.
       */

      val bound =
        expectedLength?.toDouble() ?: received.toDouble()

      progressCurrent = (received.toDouble() / bound) * 100.0
      val enoughTimeElapsed = (System.currentTimeMillis() - timeLast) >= 1000L
      if (progressCurrent >= 100.0 || enoughTimeElapsed) {
        timeLast = System.currentTimeMillis()
        this.log.debug("Download progress: {}", progressCurrent)
        this.reportProgress(request, progressCurrent.toInt())
      }
    }

    this.reportProgress(request, 100)
    outputStream.flush()
  }

  override fun close() {
    if (this.closed.compareAndSet(false, true)) {
      this.cancelAll()
    }
  }
}
