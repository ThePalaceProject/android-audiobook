package org.librarysimplified.audiobook.feedbooks

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import org.librarysimplified.audiobook.json_canon.JSONCanonicalization
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckParameters
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckResult
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckType
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.security.Signature
import java.text.ParseException

class FeedbooksSignatureCheck(
  private val httpClient: OkHttpClient,
  private val parameters: SingleLicenseCheckParameters
) : SingleLicenseCheckType {

  private val logger =
    LoggerFactory.getLogger(FeedbooksSignatureCheck::class.java)

  /*
   * Map issuer URI to certificate URL.
   */

  private val issuers = mapOf(
    "https://www.cantookaudio.com" to "https://listen.cantookaudio.com/.well-known/jwks.json"
  )

  override fun execute(): SingleLicenseCheckResult {
    this.event("Started signature check…")

    val signature =
      this.parameters.manifest.extensions.find { extension ->
        extension is FeedbooksSignature
      } as FeedbooksSignature?

    if (signature == null) {
      this.event("Check is not applicable: No signature information supplied.")
      return SingleLicenseCheckResult.NotApplicable("No signature information supplied.")
    }

    this.event("Deserializing manifest bytes…")
    val objectMapper = ObjectMapper()
    val objectNode = objectMapper.readTree(this.parameters.manifest.originalBytes) as ObjectNode

    this.event("Extracting signature from manifest…")
    val metadataNode = objectNode["metadata"]
    if (metadataNode is ObjectNode) {
      metadataNode.remove("http://www.feedbooks.com/audiobooks/signature")
    }

    this.event("Canonicalizing manifest…")
    val canonicalManifestBytes = JSONCanonicalization.canonicalize(objectNode)

    this.event("Checking signature…")
    return try {
      checkSignature(canonicalManifestBytes, signature)
    } catch (e: UnsupportedAlgorithmException) {
      val message = e.message ?: "Unsupported signature algorithm."
      this.event("Signature check failed: $message")
      SingleLicenseCheckResult.Failed(message)
    } catch (e: UnknownIssuerException) {
      val message = e.message ?: "Unknown signature issuer."
      this.event("Signature check failed: $message")
      SingleLicenseCheckResult.Failed(message)
    } catch (e: CertificateRetrievalException) {
      val message = "Certificate could not be retrieved."
      this.event("Signature check failed: $message")
      SingleLicenseCheckResult.Failed(message)
    } catch (e: ParseException) {
      val message = "Certificate could not be parsed."
      this.event("Signature check failed: $message")
      SingleLicenseCheckResult.Failed(message)
    }
  }

  private fun checkSignature(
    manifestBytes: ByteArray,
    signature: FeedbooksSignature
  ): SingleLicenseCheckResult {
    val certificate = retrieveCertificate(signature)

    if (verifySignatureWithCertificate(certificate, manifestBytes, signature)) {
      return SingleLicenseCheckResult.Succeeded("Signature verified.")
    }

    return SingleLicenseCheckResult.Failed("Signature not verified.")
  }

  private fun verifySignatureWithCertificate(
    certificateBytes: ByteArray,
    manifestBytes: ByteArray,
    signature: FeedbooksSignature
  ): Boolean {
    val keySet = JWKSet.parse(String(certificateBytes, Charsets.UTF_8))

    return keySet.keys.any({ key -> verifySignatureWithKey(key, manifestBytes, signature) })
  }

  private fun verifySignatureWithKey(
    key: JWK,
    manifestBytes: ByteArray,
    signature: FeedbooksSignature
  ): Boolean {
    when (val algorithm = signature.algorithm) {
      "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256" -> {
        val publicKey = key.toRSAKey().toPublicKey()
        val verifier = Signature.getInstance("SHA256withRSA")

        verifier.initVerify(publicKey)
        verifier.update(manifestBytes)

        return verifier.verify(Base64.from(signature.value).decode())
      }
      else -> {
        throw UnsupportedAlgorithmException("Unsupported signature algorithm $algorithm.")
      }
    }
  }

  private fun retrieveCertificate(
    signature: FeedbooksSignature
  ): ByteArray {
    val certificateURL = getCertificateURL(signature.issuer)

    this.event("Retrieving certificate $certificateURL...")

    val request =
      Request.Builder()
        .url(certificateURL)
        .header("User-Agent", this.parameters.userAgent.userAgent)
        .build()

    try {
      this.httpClient.newCall(request).execute().use { response ->
        this.logger.debug("response: {} {}", response.code, response.message)

        if (!response.isSuccessful) {
          this.event("Error downloading certificate (${response.code})")

          throw CertificateRetrievalException()
        }

        val bodyBytes = response.body?.bytes() ?: ByteArray(0)
        this.logger.debug("received {} bytes", bodyBytes.size)

        return bodyBytes
      }
    } catch (e: IOException) {
      throw CertificateRetrievalException()
    }
  }

  private fun getCertificateURL(issuerURI: String?): URL {
    val certificateUrl = issuers[issuerURI]

    if (certificateUrl == null) {
      throw UnknownIssuerException("Unknown signature issuer $issuerURI.")
    }

    return URL(certificateUrl)
  }

  private fun event(message: String) {
    this.parameters.onStatusChanged.invoke(
      SingleLicenseCheckStatus(
        source = "FeedbooksSignatureCheck",
        message = message
      )
    )
  }
}
