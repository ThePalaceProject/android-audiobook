package org.librarysimplified.audiobook.tests.open_access

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.api.PlayerDownloadRequestCredentials
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest.api.PlayerManifestEncrypted
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLinkProperties
import org.librarysimplified.audiobook.open_access.BearerTokenExtension
import org.librarysimplified.audiobook.tests.TestDirectories
import org.librarysimplified.http.api.LSHTTPAuthorizationBasic
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.LinkedList
import java.util.UUID
import java.util.concurrent.Executors

abstract class BearerTokenExtensionContract {
  private val logger = LoggerFactory.getLogger(BearerTokenExtensionContract::class.java)
  private lateinit var downloadProvider: FakeDownloadProvider
  private lateinit var executor: ListeningExecutorService

  @BeforeEach
  fun testSetup() {
    this.executor =
      MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    this.downloadProvider =
      FakeDownloadProvider()
  }

  class FakeDownloadProvider : PlayerDownloadProviderType {
    val requests =
      LinkedList<PlayerDownloadRequest>()

    override fun download(
      request: PlayerDownloadRequest
    ): ListenableFuture<Unit> {
      this.requests.add(request)
      return Futures.immediateFuture(Unit)
    }
  }

  @AfterEach
  fun testTearDown() {
    this.executor.shutdown()
  }

  /**
   * The extension doesn't handle a link when the authorization supplied to the extension is null.
   */

  @Test
  fun notUsedWhenAuthorizationIsNotBearerToken() {
    val extension = BearerTokenExtension()
    val file = File(org.librarysimplified.audiobook.tests.TestDirectories.temporaryDirectory(), UUID.randomUUID().toString())

    val request =
      PlayerDownloadRequest(
        uri = URI.create("urn:fake"),
        credentials = null,
        outputFile = file,
        onProgress = {
          this.logger.debug("progress: {}", it)
        },
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
      )

    extension.authorization = null

    val future =
      extension.onDownloadLink(
        statusExecutor = this.executor,
        downloadProvider = this.downloadProvider,
        originalRequest = request,
        link = PlayerManifestLink.LinkBasic(
          href = URI.create("urn:fake"),
        )
      )

    Assertions.assertEquals(null, future)
  }

  /**
   * The extension doesn't handle a link when the authorization supplied to the extension is not a
   * bearer token.
   */

  @Test
  fun notUsedWhenAuthorizationIsNull() {
    val extension = BearerTokenExtension()
    val file = File(org.librarysimplified.audiobook.tests.TestDirectories.temporaryDirectory(), UUID.randomUUID().toString())

    val request =
      PlayerDownloadRequest(
        uri = URI.create("urn:fake"),
        credentials = null,
        outputFile = file,
        onProgress = {
          this.logger.debug("progress: {}", it)
        },
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
      )

    extension.authorization = LSHTTPAuthorizationBasic.ofUsernamePassword(
      userName = "user",
      password = "pw"
    )

    val future =
      extension.onDownloadLink(
        statusExecutor = this.executor,
        downloadProvider = this.downloadProvider,
        originalRequest = request,
        link = PlayerManifestLink.LinkBasic(
          href = URI.create("urn:fake"),
        )
      )

    Assertions.assertEquals(null, future)
  }

  /**
   * The extension doesn't handle a link when the original request has credentials.
   */

  @Test
  fun notUsedWhenOriginalRequestHasCredentials() {
    val extension = BearerTokenExtension()
    val file = File(org.librarysimplified.audiobook.tests.TestDirectories.temporaryDirectory(), UUID.randomUUID().toString())

    val request =
      PlayerDownloadRequest(
        uri = URI.create("urn:fake"),
        credentials = PlayerDownloadRequestCredentials.Basic(
          user = "user",
          password = "pw"
        ),
        outputFile = file,
        onProgress = {
          this.logger.debug("progress: {}", it)
        },
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
      )

    extension.authorization = LSHTTPAuthorizationBearerToken.ofToken("abcd")

    val future =
      extension.onDownloadLink(
        statusExecutor = this.executor,
        downloadProvider = this.downloadProvider,
        originalRequest = request,
        link = PlayerManifestLink.LinkBasic(
          href = URI.create("urn:fake"),
        )
      )

    Assertions.assertEquals(null, future)
  }

  /**
   * The extension doesn't handle a link that contains an encryption scheme.
   */

  @Test
  fun notUsedWhenLinkHasEncryptionScheme() {
    val extension = BearerTokenExtension()
    val file = File(org.librarysimplified.audiobook.tests.TestDirectories.temporaryDirectory(), UUID.randomUUID().toString())

    val request =
      PlayerDownloadRequest(
        uri = URI.create("urn:fake"),
        credentials = null,
        outputFile = file,
        onProgress = {
          this.logger.debug("progress: {}", it)
        },
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
      )

    extension.authorization = LSHTTPAuthorizationBearerToken.ofToken("abcd")

    val future =
      extension.onDownloadLink(
        statusExecutor = this.executor,
        downloadProvider = this.downloadProvider,
        originalRequest = request,
        link = PlayerManifestLink.LinkBasic(
          href = URI.create("urn:fake"),
          properties = PlayerManifestLinkProperties(
            encrypted = PlayerManifestEncrypted(
              scheme = "scheme1"
            )
          )
        )
      )

    Assertions.assertEquals(null, future)
  }

  /**
   * The extension sends the supplied bearer token.
   */

  @Test
  fun testBearerTokenSent() {
    val extension = BearerTokenExtension()
    val file = File(org.librarysimplified.audiobook.tests.TestDirectories.temporaryDirectory(), UUID.randomUUID().toString())

    val request =
      PlayerDownloadRequest(
        uri = URI.create("urn:fake"),
        credentials = null,
        outputFile = file,
        onProgress = {
          this.logger.debug("progress: {}", it)
        },
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
      )

    extension.authorization = LSHTTPAuthorizationBearerToken.ofToken("abcd")

    val future =
      extension.onDownloadLink(
        statusExecutor = this.executor,
        downloadProvider = this.downloadProvider,
        originalRequest = request,
        link = PlayerManifestLink.LinkBasic(
          href = URI.create("urn:fake"),
        )
      )

    future!!.get()

    val sentRequest = this.downloadProvider.requests.poll()
    Assertions.assertEquals(request.uri, sentRequest.uri)
    Assertions.assertEquals(request.outputFile, sentRequest.outputFile)
    Assertions.assertTrue(sentRequest.credentials is PlayerDownloadRequestCredentials.BearerToken)

    Assertions.assertEquals(
      "abcd",
      (sentRequest.credentials as PlayerDownloadRequestCredentials.BearerToken).token
    )
  }
}
