package org.librarysimplified.audiobook.downloads

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.slf4j.LoggerFactory
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/*
 * A simple download provider.
 */

class DownloadProvider private constructor(
  private val executor: ExecutorService
) : PlayerDownloadProviderType {

  private val log =
    LoggerFactory.getLogger(DownloadProvider::class.java)

  companion object {

    /**
     * Create a new download provider.
     *
     * @param executor A listening executor that will be used for download tasks
     */

    fun create(executor: ExecutorService): PlayerDownloadProviderType {
      return DownloadProvider(executor)
    }
  }

  override fun download(request: PlayerDownloadRequest): CompletableFuture<Unit> {
    val result = CompletableFuture<Unit>()

    this.reportProgress(request, 0)

    this.executor.submit {
      try {
        result.complete(doDownload(request, result))
      } catch (_: CancellationException) {
        doCleanUp(request)
        result.cancel(true)
      } catch (e: Throwable) {
        result.completeExceptionally(e)
        doCleanUp(request)
      }
    }
    return result
  }

  private fun reportProgress(
    request: PlayerDownloadRequest,
    percent: Int
  ) {
    try {
      request.onProgress(percent)
    } catch (e: Throwable) {
      this.log.error("ignored onProgress exception: ", e)
    }
  }

  private fun doCleanUp(request: PlayerDownloadRequest) {
    this.log.debug("cleaning up output file {}", request.outputFile)
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

    val client =
      OkHttpClient.Builder()
        .connectTimeout(10L, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val httpRequestBuilder =
      Request.Builder()
        .header("User-Agent", request.userAgent.userAgent)
        .url(targetURI.toURL())

    this.configureRequestCredentials(request, httpRequestBuilder)
    val httpRequest = httpRequestBuilder.build()
    val call = client.newCall(httpRequest)
    this.log.debug("Executing HTTP request.")

    call.execute().use { response ->
      if (response.code == 401) {
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

      if (!response.isSuccessful) {
        throw IOException(
          StringBuilder(128)
            .append("Server returned an error response.\n")
            .append("  Response: ")
            .append(response.code)
            .append(' ')
            .append(response.message)
            .append('\n')
            .toString()
        )
      }

      this.handleSuccessfulResponse(response, request, result)
    }
  }

  private fun configureRequestCredentials(
    request: PlayerDownloadRequest,
    httpRequestBuilder: Request.Builder
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

    httpRequestBuilder.header("Authorization", auth.toHeaderValue())
  }

  private fun handleSuccessfulResponse(
    response: Response,
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
      response.body ?: throw IOException("HTTP server response did not contain a body")
    val expectedLength =
      body.contentLength()

    /*
     * Try to create the parent directory (and all of the required ancestors too). Ignore
     * errors, because the actual error will occur when an attempt is made to open the file.
     */

    request.outputFile.parentFile?.mkdirs()

    body.byteStream().use { inputStream ->
      FileOutputStream(request.outputFileTemp, false).use { outputStream ->
        this.copyStream(request, inputStream, outputStream, expectedLength, result)
        request.outputFileTemp.renameTo(request.outputFile)
      }
    }

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

    request.onCompletion.invoke(request.outputFile)
  }

  private fun copyStream(
    request: PlayerDownloadRequest,
    inputStream: InputStream,
    outputStream: FileOutputStream,
    expectedLength: Long,
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

      progressCurrent = (received.toDouble() / expectedLength.toDouble()) * 100.0
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
}
