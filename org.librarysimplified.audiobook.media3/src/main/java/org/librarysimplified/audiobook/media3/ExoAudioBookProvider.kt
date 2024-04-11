package org.librarysimplified.audiobook.media3

import android.app.Application
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import kotlinx.coroutines.runBlocking
import org.librarysimplified.audiobook.api.PlayerAudioBookProviderType
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerResult.Failure
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.protection.ContentProtection
import org.readium.r2.shared.util.ErrorException
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.util.concurrent.ScheduledExecutorService

/**
 * The ExoPlayer implementation of the {@link PlayerAudioBookProviderType} interface.
 */

class ExoAudioBookProvider(
  private val request: PlayerAudioEngineRequest,
  private val engineExecutor: ScheduledExecutorService,
  private val manifest: PlayerManifest,
) : PlayerAudioBookProviderType {

  override fun create(
    context: Application,
    extensions: List<PlayerExtensionType>
  ): PlayerResult<PlayerAudioBookType, Exception> {
    try {
      val scheme =
        this.manifest.metadata.encrypted?.scheme

      val dataSourceFactory: DataSource.Factory =
        when (scheme) {
          LCP_SCHEME -> {
            createLCPDataSource(
              context = context,
              file = this.request.file!!,
              contentProtections = this.request.contentProtections
            )
          }

          null -> {
            DefaultDataSource.Factory(context)
          }

          else -> {
            throw IllegalStateException("Unrecognized scheme: $scheme")
          }
        }

      val id =
        PlayerBookID.transform(this.manifest.metadata.identifier)

      return when (val parsed = ExoManifest.transform(
        context = context,
        bookID = id,
        manifest = this.manifest
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
              contentProtections = this.request.contentProtections,
              dataSourceFactory = dataSourceFactory
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
    file: File,
    contentProtections: List<ContentProtection>
  ): DataSource.Factory {
    return LCPDataSource.Factory(
      openPublication(
        context = context,
        file = file,
        contentProtections = contentProtections
      )
    )
  }

  private fun openPublication(
    context: Application,
    file: File,
    contentProtections: List<ContentProtection>
  ): Publication {
    return runBlocking {
      val httpClient =
        DefaultHttpClient()
      val assetRetriever =
        AssetRetriever(context.contentResolver, httpClient)

      when (val assetR = assetRetriever.retrieve(file)) {
        is Try.Failure -> throw ErrorException(assetR.value)
        is Try.Success -> {
          val publicationParser =
            DefaultPublicationParser(
              context = context,
              httpClient = httpClient,
              assetRetriever = assetRetriever,
              pdfFactory = LCPNoPDFFactory,
            )
          val publicationOpener =
            PublicationOpener(
              publicationParser = publicationParser,
              contentProtections = contentProtections,
              onCreatePublication = {
              },
            )

          when (val pubR = publicationOpener.open(
            asset = assetR.value,
            credentials = null,
            allowUserInteraction = false,
          )) {
            is Try.Failure -> throw ErrorException(pubR.value)
            is Try.Success -> pubR.value
          }
        }
      }
    }
  }
}
