package org.librarysimplified.audiobook.audioengine

import io.audioengine.mobile.DownloadEngine
import io.audioengine.mobile.DownloadEvent
import io.audioengine.mobile.DownloadRequest
import io.audioengine.mobile.DownloadStatus
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject

/**
 * A direct wrapper for the Findaway download engine.
 */

data class FindawayDownloadEngineDirect(
  val engine: DownloadEngine
) : FindawayDownloadEngineType {

  private val baseEvents =
    this.engine.allEvents()
  private val wrappedEvents =
    wrapObservable(this.baseEvents)

  override val events: Observable<DownloadEvent> =
    this.wrappedEvents

  override fun status(
    contentId: String,
    part: Int,
    chapter: Int
  ): Observable<DownloadStatus> {
    return wrapObservable(this.engine.getStatus(contentId, part, chapter))
  }

  private fun <T> wrapObservable(
    baseObservable: rx.Observable<T>
  ): Subject<T> {
    val subject =
      PublishSubject.create<T>()
        .toSerialized()

    baseObservable.subscribe(
      { status ->
        if (status != null) {
          subject.onNext(status)
        }
      },
      { error -> subject.onError(error) },
      { subject.onComplete() }
    )
    return subject
  }

  override fun download(request: DownloadRequest): Observable<DownloadEvent> {
    return this.wrapObservable(this.engine.download(request))
  }

  override fun pause(request: DownloadRequest) {
    return this.engine.pause(request)
  }

  override fun delete(request: DownloadRequest) {
    return this.engine.delete(request)
  }
}
