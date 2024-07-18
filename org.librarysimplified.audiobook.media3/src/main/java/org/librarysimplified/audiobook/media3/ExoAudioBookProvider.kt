package org.librarysimplified.audiobook.media3

import android.app.Application
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import kotlinx.coroutines.runBlocking
import org.librarysimplified.audiobook.api.PlayerAudioBookProviderType
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerBookCredentialsLCP
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerBookSource
import org.librarysimplified.audiobook.api.PlayerMissingTrackNameGeneratorType
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerResult.Failure
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.lcp.downloads.LCPDownloads
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.readium.r2.lcp.LcpAuthenticating
import org.readium.r2.lcp.LcpService
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.protection.ContentProtection
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.publication.services.protectionError
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.ErrorException
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.concurrent.ScheduledExecutorService

/**
 * The ExoPlayer implementation of the {@link PlayerAudioBookProviderType} interface.
 */

class ExoAudioBookProvider(
  private val request: PlayerAudioEngineRequest,
  private val engineExecutor: ScheduledExecutorService,
  private val manifest: PlayerManifest,
) : PlayerAudioBookProviderType {

  private val logger =
    LoggerFactory.getLogger(ExoAudioBookProvider::class.java)

  private var missingTrackGenerator: PlayerMissingTrackNameGeneratorType? = null

  fun setMissingTrackNameGenerator(
    generator: PlayerMissingTrackNameGeneratorType
  ) {
    this.missingTrackGenerator = generator
  }

  override fun create(
    context: Application,
    extensions: List<PlayerExtensionType>
  ): PlayerResult<PlayerAudioBookType, Exception> {
    try {
      val isLCP =
        ExoLCP.isLCP(this.request.manifest)

      /*
       * We need to decide what "downloading" means for a given book.
       *
       * If the book source is provided as a file, then we effectively disable downloading;
       * the user already has the book.
       *
       * If the book source is a license file, then this currently implies that the book is
       * LCP protected and is streamed. We offer the ability to download the entire book as
       * a file.
       *
       * Otherwise, if no book source is provided, we assume the audiobook is an unprotected
       * audiobook where individual chapters can be streamed and/or downloaded as needed.
       */

      val downloadSupport =
        when (val source = this.request.bookSource) {
          is PlayerBookSource.PlayerBookSourceFile -> {
            this.logger.debug("Book source is a file: Downloads are unsupported.")
            ExoDownloadSupport.DownloadUnsupported
          }
          is PlayerBookSource.PlayerBookSourceLicenseFile -> {
            this.logger.debug("Book source is a license file.")
            val licenseBytes = source.file.readBytes()
            when (val license = LCPDownloads.parseLicense(licenseBytes)) {
              is Failure -> {
                this.logger.debug(
                  "Failed to parse license ({}): Downloads are unsupported.",
                  license.failure.message
                )
                ExoDownloadSupport.DownloadUnsupported
              }
              is PlayerResult.Success -> {
                this.logger.debug(
                  "Successfully parsed license: Downloading entire books is supported."
                )
                ExoDownloadSupport.DownloadEntireBookAsFile(
                  targetURI = URI.create(license.result.license.publicationLink.href.toString())
                )
              }
            }
          }
          null -> {
            this.logger.debug("No book source: Downloading individual chapters is supported.")
            ExoDownloadSupport.DownloadIndividualChaptersAsFiles
          }
        }

      this.logger.debug("isLCP: {}", isLCP)
      this.logger.debug("downloadSupport: {}", downloadSupport)

      val dataSourceFactory: DataSource.Factory =
        if (isLCP) {
          this.createLCPDataSource(
            context = context,
            bookSource = request.bookSource
          )
        } else {
          DefaultDataSource.Factory(context)
        }

      val id =
        PlayerBookID.transform(this.manifest.metadata.identifier)

      val missingTrackNameGenerator =
        this.missingTrackGenerator ?: object : PlayerMissingTrackNameGeneratorType {
          override fun generateName(trackIndex: Int): String {
            return context.getString(R.string.audiobook_player_toc_track_n, trackIndex)
          }
        }

      return when (val parsed = ExoManifest.transform(
        bookID = id,
        manifest = this.manifest,
        missingTrackNames = missingTrackNameGenerator
      )) {
        is PlayerResult.Success ->
          PlayerResult.Success(
            ExoAudioBook.create(
              context = context,
              engineExecutor = this.engineExecutor,
              manifest = parsed.result,
              downloadProvider = this.request.downloadProvider,
              extensions = extensions,
              userAgent = this.request.userAgent,
              dataSourceFactory = dataSourceFactory,
              missingTrackNameGenerator = missingTrackNameGenerator,
              supportsDownloads = downloadSupport
            )
          )

        is Failure ->
          Failure(parsed.failure)
      }
    } catch (e: Exception) {
      return Failure(e)
    }
  }

  private fun createLCPDataSource(
    context: Application,
    bookSource: PlayerBookSource?
  ): DataSource.Factory {
    return when (bookSource) {
      is PlayerBookSource.PlayerBookSourceFile -> {
        LCPDataSource.Factory(
          this.openLCPPublication(
            context = context,
            file = bookSource.file,
            type = "Packaged audiobook file"
          )
        )
      }

      is PlayerBookSource.PlayerBookSourceLicenseFile -> {
        LCPDataSource.Factory(
          this.openLCPPublication(
            context = context,
            file = bookSource.file,
            type = "License file"
          )
        )
      }

      null -> {
        throw IllegalArgumentException(
          "For LCP audiobooks, either a book file or a license file is required."
        )
      }
    }
  }

  private data class ExoPublicationOpenError(
    override val message: String
  ) : Error {
    override val cause: Error? = null
  }

  private fun openLCPPublication(
    context: Application,
    file: File,
    type: String
  ): Publication {
    this.logger.debug("Opening an LCP publication of type: {}", type)
    this.logger.debug("Determining passphrase...")
    val credentialsText =
      when (val c = this.request.bookCredentials) {
        is PlayerBookCredentialsLCP -> c.passphrase
        else -> null
      }

    if (credentialsText == null) {
      this.logger.warn("No passphrase was specified. This is likely to fail.")
    }

    this.logger.debug("Creating LCP service.")
    val httpClient =
      DefaultHttpClient()
    val assetRetriever =
      AssetRetriever(context.contentResolver, httpClient)

    val lcpService =
      LcpService(
        context = context,
        assetRetriever = assetRetriever
      )

    if (lcpService == null) {
      this.logger.error("LCP service is unavailable")
      throw ErrorException(ExoPublicationOpenError("LCP service is unavailable."))
    }

    this.logger.debug("Creating LCP content protection.")
    val contentProtection = lcpService.contentProtection(
      object : LcpAuthenticating {
        override suspend fun retrievePassphrase(
          license: LcpAuthenticating.AuthenticatedLicense,
          reason: LcpAuthenticating.AuthenticationReason,
          allowUserInteraction: Boolean
        ): String? {
          return credentialsText
        }
      }
    )

    this.logger.debug("Created LCP content protection.")
    return runBlocking {
      when (val assetR = assetRetriever.retrieve(file)) {
        is Try.Failure -> throw ErrorException(assetR.value)
        is Try.Success -> {
          this@ExoAudioBookProvider.openPublicationFromAsset(
            context,
            httpClient,
            assetRetriever,
            contentProtection,
            assetR.value,
            credentialsText
          )
        }
      }
    }
  }

  private suspend fun openPublicationFromAsset(
    context: Application,
    httpClient: DefaultHttpClient,
    assetRetriever: AssetRetriever,
    contentProtection: ContentProtection,
    asset: Asset,
    credentialsText: String?
  ): Publication {
    this.logger.debug("Creating publication parser...")
    val publicationParser =
      DefaultPublicationParser(
        context = context,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = LCPNoPDFFactory,
      )

    this.logger.debug("Creating publication opener...")
    val publicationOpener =
      PublicationOpener(
        publicationParser = publicationParser,
        contentProtections = listOf(contentProtection),
        onCreatePublication = {
          this@ExoAudioBookProvider.logger.debug("onCreatePublication")
        },
      )

    return when (val pubR = publicationOpener.open(
      asset = asset,
      credentials = credentialsText,
      allowUserInteraction = false,
    )) {
      is Try.Failure -> {
        this.logger.error("Failed to open publication: {}", pubR.value)
        throw ErrorException(pubR.value)
      }

      is Try.Success -> {
        this.logger.debug("Opened publication.")
        val publication = pubR.value
        if (publication.isRestricted) {
          this.logger.error("Publication is restricted!")
          val error = publication.protectionError
          if (error != null) {
            this.logger.error("Protection error: {}", error.message)
            throw ErrorException(error)
          }

          this.logger.error("Missing a required passphrase or other credentials.")
          throw ErrorException(
            ExoPublicationOpenError("Missing a required passphrase or other credentials.")
          )
        }
        publication
      }
    }
  }
}
