package org.librarysimplified.audiobook.lcp.downloads

import android.app.Application
import kotlinx.coroutines.runBlocking
import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicParameters
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicProvider
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvent
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvents
import org.librarysimplified.http.api.LSHTTPAuthorizationBasic
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
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.asset.ContainerAsset
import org.readium.r2.shared.util.asset.DefaultArchiveOpener
import org.readium.r2.shared.util.asset.DefaultFormatSniffer
import org.readium.r2.shared.util.asset.DefaultResourceFactory
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.http.HttpRequest
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
  ): PlayerResult<LCPLicenseAndBytes, ManifestFulfillmentErrorType> {
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

  private fun parseLicense(
    data: ByteArray
  ): PlayerResult<LCPLicenseAndBytes, ManifestFulfillmentErrorType> {
    return when (val result = LicenseDocument.fromBytes(data)) {
      is Try.Failure -> {
        this.logger.debug("Failed to parse LCP license: {}", result.value.message)
        PlayerResult.Failure(LCPLicenseFailure(result.value.message))
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

    val request: LSHTTPRequestType =
      parameters.httpClient.newRequest(URI.create(license.publicationLink.href.toString()))
        .apply {
          val credentials = parameters.credentials
          if (credentials != null) {
            this.setAuthorization(
              LSHTTPAuthorizationBasic.ofUsernamePassword(
                credentials.userName,
                credentials.password
              )
            )
          }
        }
        .addHeader("User-Agent", parameters.userAgent.userAgent)
        .build()

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
    licenseAndBytes: LCPLicenseAndBytes,
    file: File,
    fileTemp: File
  ): ManifestFulfilled {
    this.logger.debug("Repackaging publication {}", file)

    val manifestBytes =
      ZipOutputStream(FileOutputStream(fileTemp)).use { zipOut ->
        val lcpEntry = ZipEntry("license.lcpl")
        zipOut.putNextEntry(lcpEntry)
        zipOut.write(licenseAndBytes.licenseBytes)
        zipOut.closeEntry()

        ZipFile(file).use { zipIn ->
          var manifestBytes: ByteArray? = null

          for (entry in zipIn.entries()) {
            this.logger.debug("Entry: {} (size {})", entry.name, entry.size)
            zipIn.getInputStream(entry).use { inStream ->
              val data = inStream.readBytes()
              val crc = CRC32()
              crc.update(data)

              val entryCopy = ZipEntry(entry.name)
              entryCopy.method = entry.method
              entryCopy.size = data.size.toLong()
              entryCopy.crc = crc.value

              zipOut.putNextEntry(entryCopy)
              if (entry.name == "manifest.json") {
                manifestBytes = data
              }
              zipOut.write(data)
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
      contentType = MIMEType("text", "json", mapOf()),
      authorization = null,
      data = manifestBytes!!
    )
  }

  private data class LicenseError(
    override val message: String
  ) : ManifestFulfillmentErrorType {
    override val serverData: ManifestFulfillmentErrorType.ServerData? =
      null
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
    parameters: ManifestFulfillmentBasicParameters,
    license: LicenseDocument,
    receiver: (ManifestFulfillmentEvent) -> Unit
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentErrorType> {
    return runBlocking {
      when (val r = downloadManifestTextFromLicenseFile(context, license, parameters, receiver)) {
        is PlayerResult.Failure -> PlayerResult.Failure(r.failure)
        is PlayerResult.Success -> {
          PlayerResult.Success(
            ManifestFulfilled(
              contentType = MIMEType("text", "json", mapOf()),
              authorization = null,
              data = r.result
            )
          )
        }
      }
    }
  }

  private suspend fun downloadManifestTextFromLicenseFile(
    context: Application,
    license: LicenseDocument,
    parameters: ManifestFulfillmentBasicParameters,
    receiver: (ManifestFulfillmentEvent) -> Unit
  ): PlayerResult<ByteArray, ManifestFulfillmentErrorType> {
    this.logger.debug("Downloading manifest text from LCP license file.")

    val absoluteUrl = AbsoluteUrl.invoke(license.publicationLink.href.toString())
    if (absoluteUrl == null) {
      this.logger.debug(
        "Could not parse publication link {} as an absolute URL", license.publicationLink.href
      )
      return PlayerResult.Failure(LicenseError("Publication link cannot be resolved."))
    }

    val inputCredentials =
      parameters.credentials
    val credentials =
      if (inputCredentials != null) {
        LSHTTPAuthorizationBasic.ofUsernamePassword(
          userName = inputCredentials.userName,
          password = inputCredentials.password
        )
      } else {
        null
      }

    val httpClient =
      DefaultHttpClient(callback = object : DefaultHttpClient.Callback {
        override suspend fun onStartRequest(
          request: HttpRequest
        ): HttpTry<HttpRequest> {
          val newRequest: HttpRequest = request.copy {
            if (credentials != null) {
              this.headers.put("Authorization", mutableListOf(credentials.toHeaderValue()))
            }
          }
          return super.onStartRequest(newRequest)
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
          LicenseError(message = result.value.message)
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
  ): PlayerResult<ByteArray, ManifestFulfillmentErrorType> {
    if (asset !is ContainerAsset) {
      this.logger.debug("Retrieved asset is not a container asset ({})", asset.javaClass)
      return PlayerResult.Failure(
        LicenseError(message = "Asset is not a container asset.")
      )
    }

    receiver.invoke(ManifestFulfillmentEvent("Attempting to extract manifest..."))
    val manifestURL = Url("manifest.json")!!
    val resource = asset.container[manifestURL]
    if (resource == null) {
      this.logger.debug("Container does not contain '{}'", manifestURL)
      return PlayerResult.Failure(
        LicenseError(message = "Container does not appear to contain manifest.json")
      )
    }

    receiver.invoke(ManifestFulfillmentEvent("Reading manifest bytes..."))
    return when (val r = resource.read()) {
      is Try.Failure -> {
        this.logger.error("Reading resource failed: {}", r.value)
        PlayerResult.Failure(LicenseError(message = r.value.message))
      }
      is Try.Success -> {
        PlayerResult.Success(r.value)
      }
    }
  }
}
