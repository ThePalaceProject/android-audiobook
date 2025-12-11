package org.librarysimplified.audiobook.tests

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentCredentialsBasic
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicParameters
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicProvider
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPRequestProperties
import org.librarysimplified.http.api.LSHTTPRequestType
import org.librarysimplified.http.vanilla.internal.LSHTTPClient
import org.librarysimplified.http.vanilla.internal.LSHTTPResponse
import org.mockito.Mockito
import org.mockito.kotlin.any
import java.net.URI
import java.net.URL

abstract class ManifestFulfillmentBasicContract {

  private lateinit var client: LSHTTPClient
  private lateinit var requestBuilder: LSHTTPRequestBuilderType
  private lateinit var request: LSHTTPRequestType

  @BeforeEach
  fun testSetup() {
    this.client =
      Mockito.mock(LSHTTPClient::class.java)
    this.requestBuilder =
      Mockito.mock(LSHTTPRequestBuilderType::class.java)
    this.request =
      Mockito.mock(LSHTTPRequestType::class.java)

    Mockito.`when`(this.client.newRequest(any<URI>()))
      .thenReturn(this.requestBuilder)

    Mockito.`when`(this.requestBuilder.setAuthorization(any()))
      .thenReturn(this.requestBuilder)
    Mockito.`when`(this.requestBuilder.addHeader(any(), any()))
      .thenReturn(this.requestBuilder)
    Mockito.`when`(this.requestBuilder.allowRedirects(any()))
      .thenReturn(this.requestBuilder)
    Mockito.`when`(this.requestBuilder.build())
      .thenReturn(this.request)
  }

  /**
   * If the server returns a 404 code, the request fails.
   */

  @Test
  fun test404() {
    val response = LSHTTPResponse.ofOkResponse(
      client = this.client,
      response = Response.Builder()
        .code(404)
        .message("NOT FOUND")
        .protocol(Protocol.HTTP_1_1)
        .request(
          Request.Builder()
            .url(URL("http://www.example.com"))
            .build()
        )
        .build()
    )

    Mockito.`when`(this.request.execute())
      .thenReturn(response)

    val provider =
      ManifestFulfillmentBasicProvider()

    val strategy =
      provider.create(
        configuration = ManifestFulfillmentBasicParameters(
          userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0"),
          uri = URI.create("http://www.example.com"),
          credentials = ManifestFulfillmentCredentialsBasic(
            userName = "user",
            password = "password"
          ),
          httpClient = this.client
        )
      )

    val result =
      strategy.execute() as PlayerResult.Failure

    val error = result.failure
    val serverData = error.serverData!!
    Assertions.assertEquals(404, serverData.code)
    Assertions.assertEquals("NOT FOUND", error.message)
    Assertions.assertArrayEquals(ByteArray(0), serverData.receivedBody)
    Assertions.assertEquals("application/octet-stream", serverData.receivedContentType)
  }

  /**
   * If the server returns a data, the data is returned!
   */

  @Test
  fun testOK() {
    val response = LSHTTPResponse.ofOkResponse(
      client = this.client,
      response = Response.Builder()
        .code(200)
        .message("OK")
        .protocol(Protocol.HTTP_1_1)
        .request(
          Request.Builder()
            .url(URL("http://www.example.com"))
            .build()
        )
        .body(
          ResponseBody.create(
            "text/plain".toMediaTypeOrNull(),
            "Some text."
          )
        )
        .build()
    )

    Mockito.`when`(this.request.execute())
      .thenReturn(response)

    val provider =
      ManifestFulfillmentBasicProvider()

    val strategy =
      provider.create(
        configuration = ManifestFulfillmentBasicParameters(
          userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0"),
          uri = URI.create("http://www.example.com"),
          credentials = ManifestFulfillmentCredentialsBasic(
            userName = "user",
            password = "password"
          ),
          httpClient = this.client
        )
      )

    val result =
      strategy.execute() as PlayerResult.Success

    val data = result.result
    Assertions.assertEquals("Some text.", String(data.data))
  }

  /**
   * The authorization used to perform the request is returned.
   */

  @Test
  fun testBearerToken() {
    val response = LSHTTPResponse.ofOkResponse(
      client = this.client,
      response = Response.Builder()
        .code(200)
        .message("OK")
        .protocol(Protocol.HTTP_1_1)
        .request(
          Request.Builder()
            .url(URL("http://www.example.com"))
            .tag(LSHTTPRequestProperties::class.java, LSHTTPRequestProperties(
              target = URI("http://www.example.com"),
              cookies = sortedMapOf(),
              headers = sortedMapOf(),
              method = LSHTTPRequestBuilderType.Method.Get,
              authorization = LSHTTPAuthorizationBearerToken.ofToken("abcd"),
              otherProperties = mapOf()
            ))
            .build()
        )
        .body(
          ResponseBody.create(
            "text/plain".toMediaTypeOrNull(),
            "Some text."
          )
        )
        .build()
    )

    Mockito.`when`(this.request.execute())
      .thenReturn(response)

    val provider =
      ManifestFulfillmentBasicProvider()

    val strategy =
      provider.create(
        configuration = ManifestFulfillmentBasicParameters(
          userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0"),
          uri = URI.create("http://www.example.com"),
          credentials = ManifestFulfillmentCredentialsBasic(
            userName = "user",
            password = "password"
          ),
          httpClient = this.client
        )
      )

    val result =
      strategy.execute() as PlayerResult.Success

    val data = result.result
    Assertions.assertEquals("Bearer abcd", data.authorization?.toHeaderValue())
  }
}
