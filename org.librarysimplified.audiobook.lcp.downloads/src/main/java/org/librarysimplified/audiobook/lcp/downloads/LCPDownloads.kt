package org.librarysimplified.audiobook.lcp.downloads

import android.app.Application
import kotlinx.coroutines.runBlocking
import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest.Kind.MANIFEST
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicParameters
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicProvider
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentError
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvent
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvents
import org.librarysimplified.http.api.LSHTTPRequestType
import org.librarysimplified.http.downloads.LSHTTPDownloadRequest
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCancelled
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCompletedSuccessfully
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedExceptionally
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedServer
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedUnacceptableMIME
import org.librarysimplified.http.downloads.LSHTTPDownloads
import org.readium.r2.lcp.license.model.LicenseDocument
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.asset.ContainerAsset
import org.readium.r2.shared.util.asset.DefaultArchiveOpener
import org.readium.r2.shared.util.asset.DefaultFormatSniffer
import org.readium.r2.shared.util.asset.DefaultResourceFactory
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpStatus
import org.readium.r2.shared.util.http.HttpTry
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException

object LCPDownloads {

  private val logger =
    LoggerFactory.getLogger(LCPDownloads::class.java)

  /**
   * We don't currently support downloading LCP artifacts over anything other than basic
   * authentication.
   */

  private val strategyProvider =
    ManifestFulfillmentBasicProvider()

  /**
   * Download and parse an LCP license file.
   */

  fun downloadLicense(
    parameters: ManifestFulfillmentBasicParameters,
    receiver: (ManifestFulfillmentEvent) -> Unit
  ): PlayerResult<LCPLicenseAndBytes, ManifestFulfillmentError> {
    this.logger.debug("Downloading LCP license...")

    val strategy =
      this.strategyProvider.create(parameters)
    val subscription =
      strategy.events.subscribe(receiver)

    return try {
      when (val result = strategy.execute()) {
        is PlayerResult.Failure -> {
          this.logger.debug("Failed to download LCP license.")
          PlayerResult.Failure(result.failure)
        }

        is PlayerResult.Success -> {
          this.logger.debug("Downloaded LCP license. Parsing...")
          this.parseLicense(result.result.data)
        }
      }
    } finally {
      subscription.dispose()
    }
  }

  /**
   * Parse a license file.
   */

  fun parseLicense(
    data: ByteArray
  ): PlayerResult<LCPLicenseAndBytes, ManifestFulfillmentError> {
    return when (val result = LicenseDocument.fromBytes(data)) {
      is Try.Failure -> {
        this.logger.debug("Failed to parse LCP license: {}", result.value.message)
        PlayerResult.Failure(
          ManifestFulfillmentError(
            message = result.value.message,
            extraMessages = this.accumulateErrorMessages(result),
            serverData = null
          )
        )
      }

      is Try.Success -> {
        this.logger.debug("Successfully parsed LCP license.")
        PlayerResult.Success(
          LCPLicenseAndBytes(
            license = result.value,
            licenseBytes = data
          )
        )
      }
    }
  }

  /**
   * Download an LCP publication.
   */

  fun downloadPublication(
    parameters: ManifestFulfillmentBasicParameters,
    license: LicenseDocument,
    outputFile: File,
    isCancelled: () -> Boolean,
    receiver: (ManifestFulfillmentEvent) -> Unit
  ): PlayerResult<Unit, LSHTTPDownloadResult.DownloadFailed> {
    this.logger.debug("Downloading LCP publication {}...", license.publicationLink.href)

    val link =
      PlayerManifestLink.LinkBasic(URI.create(license.publicationLink.href.toString()))
    val requestBuilder =
      parameters.httpClient.newRequest(link.href!!)

    requestBuilder.setAuthorization(
      parameters.authorizationHandler.onConfigureAuthorizationFor(link, MANIFEST)
    )

    requestBuilder.addHeader("User-Agent", parameters.userAgent.userAgent)
    val request: LSHTTPRequestType = requestBuilder.build()

    val downloadRequest =
      LSHTTPDownloadRequest(
        request = request,
        outputFile = outputFile,
        onEvent = { state -> receiver(ManifestFulfillmentEvents.ofDownloadState(state)) },
        isMIMETypeAcceptable = { true },
        isCancelled = isCancelled
      )

    val download =
      LSHTTPDownloads.create(downloadRequest)

    outputFile.delete()

    return when (val result = download.execute()) {
      DownloadCancelled -> {
        throw CancellationException()
      }

      is DownloadCompletedSuccessfully -> {
        this.logger.debug("Downloaded {} -> {}", license.publicationLink.href, outputFile)
        PlayerResult.Success(Unit)
      }

      is DownloadFailedExceptionally -> {
        PlayerResult.Failure(result)
      }

      is DownloadFailedServer -> {
        PlayerResult.Failure(result)
      }

      is DownloadFailedUnacceptableMIME -> {
        PlayerResult.Failure(result)
      }
    }
  }

  /**
   * Repackage a publication by inserting the given license into it.
   */

  fun repackagePublication(
    source: URI,
    licenseBytes: ByteArray,
    file: File,
    fileTemp: File
  ): ManifestFulfilled {
    this.logger.debug("Repackaging publication {}", file)

    val manifestBytes =
      ZipOutputStream(FileOutputStream(fileTemp)).use { zipOut ->
        val lcpEntry = ZipEntry("license.lcpl")
        zipOut.putNextEntry(lcpEntry)
        zipOut.write(licenseBytes)
        zipOut.closeEntry()

        ZipFile(file).use { zipIn ->
          var manifestBytes: ByteArray? = null

          for (entry in zipIn.entries()) {
            this.logger.debug("Entry: {} (size {})", entry.name, entry.size)

            if (entry.name == "manifest.json") {
              manifestBytes = zipIn.getInputStream(entry).use { s -> s.readBytes() }
            }

            zipIn.getInputStream(entry).use { inStream ->
              val crc = CRC32()
              val buffer = ByteArray(4096)
              var size = 0L
              while (true) {
                val r = inStream.read(buffer, 0, buffer.size)
                if (r == -1) {
                  break
                }
                size += r
                crc.update(buffer, 0, r)
              }

              val entryCopy = ZipEntry(entry.name)
              entryCopy.method = entry.method
              entryCopy.size = size
              entryCopy.crc = crc.value

              zipOut.putNextEntry(entryCopy)
            }

            zipIn.getInputStream(entry).use { inStream ->
              val buffer = ByteArray(4096)
              while (true) {
                val r = inStream.read(buffer, 0, buffer.size)
                if (r == -1) {
                  break
                }
                zipOut.write(buffer, 0, r)
              }
              zipOut.closeEntry()
            }
          }
          manifestBytes
        }
      }

    Files.move(
      fileTemp.toPath(),
      file.toPath(),
      StandardCopyOption.ATOMIC_MOVE,
      StandardCopyOption.REPLACE_EXISTING
    )

    return ManifestFulfilled(
      source = source,
      contentType = MIMEType("text", "json", mapOf()),
      authorization = null,
      data = manifestBytes!!
    )
  }

  /**
   * Download a manifest from an LCP license publication.
   *
   * This uses R2 internally to stream the contents of a remote zip file without having to
   * download the entire file. This allows for extracting the manifest from a remote publication
   * without having to wait for the entire publication to be downloaded.
   */

  fun downloadManifestFromPublication(
    context: Application,
    license: LicenseDocument,
    authorizationHandler: PlayerAuthorizationHandlerType,
    receiver: (ManifestFulfillmentEvent) -> Unit
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentError> {
    return runBlocking {
      when (val r = this@LCPDownloads.downloadManifestTextFromLicenseFile(
        context,
        license,
        authorizationHandler,
        receiver
      )) {
        is PlayerResult.Failure -> PlayerResult.Failure(r.failure)
        is PlayerResult.Success -> r
      }
    }
  }

  private suspend fun downloadManifestTextFromLicenseFile(
    context: Application,
    license: LicenseDocument,
    authorizationHandler: PlayerAuthorizationHandlerType,
    receiver: (ManifestFulfillmentEvent) -> Unit
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentError> {
    this.logger.debug("Downloading manifest text from LCP license file.")

    val absoluteUrl = AbsoluteUrl.invoke(license.publicationLink.href.toString())
    if (absoluteUrl == null) {
      this.logger.debug(
        "Could not parse publication link {} as an absolute URL", license.publicationLink.href
      )
      return PlayerResult.Failure(
        ManifestFulfillmentError(
          message = "Publication link cannot be resolved.",
          extraMessages = listOf(),
          serverData = null
        )
      )
    }

    val link =
      PlayerManifestLink.LinkBasic(URI.create(absoluteUrl.toString()))
    val credentials =
      authorizationHandler.onConfigureAuthorizationFor(link, MANIFEST)

    val httpClient =
      DefaultHttpClient(callback = object : DefaultHttpClient.Callback {
        override suspend fun onRecoverRequest(
          request: HttpRequest,
          error: HttpError
        ): HttpTry<HttpRequest> {
          this@LCPDownloads.logger.debug("HTTP request failed ({})", error)
          return when (error) {
            is HttpError.ErrorResponse -> {
              if (error.status == HttpStatus.Unauthorized) {
                if (credentials != null) {
                  this@LCPDownloads.logger.debug("Retrying request with added credentials.")
                  val newRequest: HttpRequest = request.copy {
                    this.headers.put("Authorization", mutableListOf(credentials.toHeaderValue()))
                  }
                  Try.success(newRequest)
                } else {
                  this@LCPDownloads.logger.debug("We have no credentials with which to retry the request.")
                  Try.failure(error)
                }
              } else {
                Try.failure(error)
              }
            }

            is HttpError.IO,
            is HttpError.MalformedResponse,
            is HttpError.Redirection,
            is HttpError.SslHandshake,
            is HttpError.Timeout,
            is HttpError.Unreachable ->
              Try.failure(error)
          }
        }
      })

    val assetRetriever =
      AssetRetriever(
        DefaultResourceFactory(context.contentResolver, httpClient),
        DefaultArchiveOpener(),
        DefaultFormatSniffer()
      )

    receiver.invoke(ManifestFulfillmentEvent("Retrieving $absoluteUrl"))
    return when (val result = assetRetriever.retrieve(absoluteUrl)) {
      is Try.Failure -> {
        this.logger.error("Failed to retrieve URL: {}", result.value.message)
        PlayerResult.Failure(
          ManifestFulfillmentError(
            message = result.value.message,
            extraMessages = this.accumulateErrorMessages(result),
            serverData = null
          )
        )
      }

      is Try.Success -> {
        this.logger.debug("Extracting manifest...")
        this@LCPDownloads.extractManifest(result.value, receiver)
      }
    }
  }

  private suspend fun extractManifest(
    asset: Asset,
    receiver: (ManifestFulfillmentEvent) -> Unit
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentError> {
    if (asset !is ContainerAsset) {
      this.logger.debug("Retrieved asset is not a container asset ({})", asset.javaClass)
      return PlayerResult.Failure(
        ManifestFulfillmentError(
          message = "Asset is not a container asset.",
          extraMessages = listOf("Asset is of type ${asset.javaClass}."),
          serverData = null
        )
      )
    }

    receiver.invoke(ManifestFulfillmentEvent("Attempting to extract manifest..."))
    val manifestURL = Url("manifest.json")!!
    val resource = asset.container[manifestURL]
    if (resource == null) {
      this.logger.debug("Container does not contain '{}'", manifestURL)
      return PlayerResult.Failure(
        ManifestFulfillmentError(
          message = "Container does not appear to contain manifest.json",
          extraMessages = listOf(),
          serverData = null
        )
      )
    }

    receiver.invoke(ManifestFulfillmentEvent("Reading manifest bytes..."))
    return when (val r = resource.read()) {
      is Try.Failure -> {
        this.logger.error("Reading resource failed: {}", r.value)
        return PlayerResult.Failure(
          ManifestFulfillmentError(
            message = "Reading manifest bytes failed.",
            extraMessages = accumulateErrorMessages(r),
            serverData = null
          )
        )
      }

      is Try.Success -> {
        PlayerResult.Success(
          ManifestFulfilled(
            source = URI.create("manifest.json"),
            contentType = MIMEType("text", "json", mapOf()),
            data = r.value
          )
        )
      }
    }
  }

  private fun accumulateErrorMessages(
    failure: Try.Failure<*, Error>
  ): List<String> {
    val messages = mutableListOf<String>()
    var errorNow: Error? = failure.value
    while (true) {
      if (errorNow == null) {
        break
      }
      messages.add(errorNow.message)
      when (val e = errorNow) {
        is HttpError.ErrorResponse -> {
          messages.add("URL returned HTTP status ${e.status}.")
          val problemDetails = e.problemDetails
          if (problemDetails != null) {
            messages.add("Problem details [Title]:    ${problemDetails.title}")
            messages.add("Problem details [Detail]:   ${problemDetails.detail}")
            messages.add("Problem details [Type]:     ${problemDetails.type}")
            messages.add("Problem details [Instance]: ${problemDetails.instance}")
            messages.add("Problem details [Status]:   ${problemDetails.status}")
          }
        }
      }
      errorNow = errorNow.cause
    }
    return messages.toList()
  }

  /**
   * Extract a manifest from the given book file.
   */

  fun extractManifestFromFile(
    context: Application,
    bookFile: File,
    receiver: (ManifestFulfillmentEvent) -> Unit
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentError> {
    return runBlocking {
      val assetRetriever =
        AssetRetriever(
          DefaultResourceFactory(context.contentResolver, DefaultHttpClient()),
          DefaultArchiveOpener(),
          DefaultFormatSniffer()
        )

      when (val result = assetRetriever.retrieve(bookFile)) {
        is Try.Failure -> {
          logger.error("Failed to retrieve URL: {}", result.value.message)
          PlayerResult.Failure(
            ManifestFulfillmentError(
              message = result.value.message,
              extraMessages = accumulateErrorMessages(result),
              serverData = null
            )
          )
        }

        is Try.Success -> {
          logger.debug("Extracting manifest...")
          this@LCPDownloads.extractManifest(result.value, receiver)
        }
      }
    }
  }
}
