package org.librarysimplified.audiobook.feedbooks

import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.api.extensions.PlayerAuthorizationHandlerExtensionType
import org.librarysimplified.audiobook.json_web_token.JOSEHeader
import org.librarysimplified.audiobook.json_web_token.JSONWebSignature
import org.librarysimplified.audiobook.json_web_token.JSONWebSignatureAlgorithmHMACSha256
import org.librarysimplified.audiobook.json_web_token.JSONWebTokenClaims
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * A player extension for Feedbooks audio books.
 */

class FeedbooksPlayerExtension : PlayerAuthorizationHandlerExtensionType {

  /**
   * The configuration data required for operation. If no configuration information
   * is provided, the extension is disabled.
   */

  @Volatile
  var configuration: FeedbooksPlayerExtensionConfiguration? = null

  private val logger =
    LoggerFactory.getLogger(FeedbooksPlayerExtension::class.java)

  override val name: String =
    "org.librarysimplified.audiobook.feedbooks"

  override fun onOverrideAuthorizationFor(
    link: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind,
    authorization: LSHTTPAuthorizationType?
  ): PlayerAuthorizationHandlerExtensionType.AuthenticationOverrideType {
    return when (kind) {
      PlayerDownloadRequest.Kind.CHAPTER ->
        this.onOverrideAuthorizationForChapter(link)

      PlayerDownloadRequest.Kind.MANIFEST ->
        PlayerAuthorizationHandlerExtensionType.OverrideNotApplicable

      PlayerDownloadRequest.Kind.WHOLE_BOOK ->
        PlayerAuthorizationHandlerExtensionType.OverrideNotApplicable

      PlayerDownloadRequest.Kind.LICENSE ->
        PlayerAuthorizationHandlerExtensionType.OverrideNotApplicable
    }
  }

  private fun onOverrideAuthorizationForChapter(
    link: PlayerManifestLink
  ): PlayerAuthorizationHandlerExtensionType.AuthenticationOverrideType {
    return when (link.properties.encrypted?.scheme) {
      "http://www.feedbooks.com/audiobooks/access-restriction" ->
        this.generateBearerTokenForChapter(link)

      else ->
        PlayerAuthorizationHandlerExtensionType.OverrideNotApplicable
    }
  }

  private fun generateBearerTokenForChapter(
    link: PlayerManifestLink
  ): PlayerAuthorizationHandlerExtensionType.AuthenticationOverrideType {
    val currentConfiguration = this.configuration
    if (currentConfiguration == null) {
      val message =
        "Link requires Feedbooks support, but the Feedbooks extension has not been configured."
      this.logger.warn(message)
      return PlayerAuthorizationHandlerExtensionType.OverrideError(message)
    }

    val tokenHeader =
      JOSEHeader(
        mapOf(
          Pair("alg", "HS256"),
          Pair("typ", "JWT")
        )
      )

    val targetRaw =
      link.hrefURI
    val target =
      targetRaw?.toString()
    if (target == null) {
      val message = "Link is missing a href URI!"
      this.logger.warn(message)
      return PlayerAuthorizationHandlerExtensionType.OverrideError(message)
    }

    val tokenClaims =
      JSONWebTokenClaims(
        mapOf(
          Pair("iss", currentConfiguration.issuerURL),
          Pair("sub", target),
          Pair("jti", UUID.randomUUID().toString())
        )
      )

    val token =
      JSONWebSignature.create(
        algorithm = JSONWebSignatureAlgorithmHMACSha256.withSecret(
          currentConfiguration.bearerTokenSecret
        ),
        header = tokenHeader,
        payload = tokenClaims
      )

    this.logger.debug("Overrode link {} authorization with JWT bearer token.", target)
    return PlayerAuthorizationHandlerExtensionType.OverrideWith(
      LSHTTPAuthorizationBearerToken.ofToken(token.encode())
    )
  }
}
