package org.librarysimplified.audiobook.lcp.downloads

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
import org.readium.r2.shared.util.Try
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
            setAuthorization(
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
        val lcpEntry = ZipEntry("META-INF/license.lcpl")
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
}
