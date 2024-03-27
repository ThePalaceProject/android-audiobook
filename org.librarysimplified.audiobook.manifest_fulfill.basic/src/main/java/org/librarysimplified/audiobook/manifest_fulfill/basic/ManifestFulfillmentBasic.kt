package org.librarysimplified.audiobook.manifest_fulfill.basic

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import okhttp3.OkHttpClient
import one.irradia.mime.vanilla.MIMEParser
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvent
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType
import org.librarysimplified.http.api.LSHTTPAuthorizationBasic
import org.librarysimplified.http.api.LSHTTPRequestBuilderType.AllowRedirects.ALLOW_UNSAFE_REDIRECTS
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.slf4j.LoggerFactory

/**
 * A fulfillment strategy that expects to receive a manifest directly, via HTTP basic authentication.
 */

class ManifestFulfillmentBasic(
  private val client: OkHttpClient,
  private val configuration: ManifestFulfillmentBasicParameters
) : ManifestFulfillmentStrategyType {

  private val logger =
    LoggerFactory.getLogger(ManifestFulfillmentBasic::class.java)

  private val eventSubject =
    PublishSubject.create<ManifestFulfillmentEvent>()

  override val events: Observable<ManifestFulfillmentEvent> =
    this.eventSubject

  override fun execute(): PlayerResult<ManifestFulfilled, ManifestFulfillmentErrorType> {
    this.logger.debug("fulfilling manifest: {}", this.configuration.uri)

    this.eventSubject.onNext(
      ManifestFulfillmentEvent(
        "Fulfilling ${this.configuration.uri}"
      )
    )

    val credentials = this.configuration.credentials
    val httpClient = this.configuration.httpClient

    val request = httpClient.newRequest(this.configuration.uri)
      .apply {
        if (credentials != null) {
          setAuthorization(
            LSHTTPAuthorizationBasic.ofUsernamePassword(
              credentials.userName,
              credentials.password
            )
          )
        }
      }
      .addHeader("User-Agent", this.configuration.userAgent.userAgent)
      .allowRedirects(ALLOW_UNSAFE_REDIRECTS)
      .build()

    val response = request.execute()

    val responseCode = response.properties?.status ?: 0
    val responseMessage = response.properties?.message ?: ""
    val contentType = response.properties?.contentType?.toString() ?: "application/octet-stream"

    this.logger.debug(
      "received: {} {} for {} ({})",
      responseCode,
      responseMessage,
      this.configuration.uri,
      contentType
    )

    this.eventSubject.onNext(
      ManifestFulfillmentEvent(
        "Received $responseCode $responseMessage for ${this.configuration.uri}"
      )
    )

    return when (val status = response.status) {
      is LSHTTPResponseStatus.Responded.OK -> {
        PlayerResult.unit(
          ManifestFulfilled(
            contentType = MIMEParser.parseRaisingException(contentType),
            authorization = status.properties.authorization,
            data = status.bodyStream?.readBytes() ?: ByteArray(0)
          )
        )
      }
      is LSHTTPResponseStatus.Responded.Error -> {
        PlayerResult.Failure(
          HTTPRequestFailed(
            message = responseMessage,
            serverData = ManifestFulfillmentErrorType.ServerData(
              uri = this.configuration.uri,
              code = responseCode,
              receivedBody = status.bodyStream?.readBytes() ?: ByteArray(0),
              receivedContentType = contentType
            )
          )
        )
      }
      is LSHTTPResponseStatus.Failed -> {
        PlayerResult.Failure(
          HTTPRequestFailed(
            message = responseMessage,
            serverData = ManifestFulfillmentErrorType.ServerData(
              uri = this.configuration.uri,
              code = responseCode,
              receivedBody = ByteArray(0),
              receivedContentType = contentType
            )
          )
        )
      }
    }
  }

  override fun close() {
    this.eventSubject.onComplete()
  }
}
