package org.librarysimplified.audiobook.lcp

import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.runBlocking
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerPlaybackIntention
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPlaybackStatus
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.ErrorException
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LCPAudioBookPlayer private constructor(
  private val book: LCPAudioBook,
  private val dataSourceFactory: LCPDataSource.Factory,
  private val engineExecutor: ScheduledExecutorService,
  private val exoPlayer: ExoPlayer,
  private val statusEvents: BehaviorSubject<PlayerEvent>,
) : PlayerType {

  private val log =
    LoggerFactory.getLogger(LCPAudioBookPlayer::class.java)

  companion object {

    private const val TIMEOUT_PLAYER_CREATION = 5L

    fun create(
      book: LCPAudioBook,
      context: Application,
      engineExecutor: ScheduledExecutorService,
      manualPassphrase: Boolean
    ): LCPAudioBookPlayer {
      val statusEvents =
        BehaviorSubject.create<PlayerEvent>()

      /*
       * The Media3 audio player now has the restriction that it must be created on the UI thread.
       */

      val exoFuture = CompletableFuture<ExoPlayer>()
      PlayerUIThread.runOnUIThread {
        exoFuture.complete(ExoPlayer.Builder(context).build())
      }
      val exoPlayer =
        exoFuture.get(this.TIMEOUT_PLAYER_CREATION, TimeUnit.SECONDS)
      val publication =
        this.openPublication(context, book)

      return LCPAudioBookPlayer(
        book = book,
        dataSourceFactory = LCPDataSource.Factory(publication),
        engineExecutor = engineExecutor,
        exoPlayer = exoPlayer,
        statusEvents = statusEvents,
      )
    }

    private fun openPublication(
      context: Application,
      book: LCPAudioBook
    ): Publication {
      return runBlocking {
        val httpClient =
          DefaultHttpClient()
        val assetRetriever =
          AssetRetriever(context.contentResolver, httpClient)

        when (val assetR = assetRetriever.retrieve(book.file)) {
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
                contentProtections = book.contentProtections,
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

  private val closed =
    AtomicBoolean(false)

  @Volatile
  private var currentPlaybackRate: PlayerPlaybackRate = PlayerPlaybackRate.NORMAL_TIME
  override fun close() {
    TODO("Not yet implemented")
  }

  override val playbackStatus: PlayerPlaybackStatus
    get() = TODO("Not yet implemented")
  override val playbackIntention: PlayerPlaybackIntention
    get() = TODO("Not yet implemented")

  override var playbackRate: PlayerPlaybackRate
    get() = TODO("Not yet implemented")
    set(value) {}
  override val isClosed: Boolean
    get() = TODO("Not yet implemented")
  override val events: Observable<PlayerEvent>
    get() = TODO("Not yet implemented")

  override fun play() {
    TODO("Not yet implemented")
  }

  override fun pause() {
    TODO("Not yet implemented")
  }

  override fun skipToNextChapter(offset: Long) {
    TODO("Not yet implemented")
  }

  override fun skipToPreviousChapter(offset: Long) {
    TODO("Not yet implemented")
  }

  override fun skipPlayhead(milliseconds: Long) {
    TODO("Not yet implemented")
  }

  override fun movePlayheadToLocation(location: PlayerPosition) {
    TODO("Not yet implemented")
  }

  override fun movePlayheadToBookStart() {
    TODO("Not yet implemented")
  }

  override fun getCurrentPositionAsPlayerBookmark(): PlayerBookmark? {
    TODO("Not yet implemented")
  }
}
