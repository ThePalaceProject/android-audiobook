package org.librarysimplified.audiobook.audioengine

import io.audioengine.mobile.DownloadEvent
import io.audioengine.mobile.DownloadRequest
import io.audioengine.mobile.DownloadStatus
import io.reactivex.Observable

/**
 * An interface exposed by download engines. This interface mirrors the interface exposed by
 * the Findaway audio engine.
 */

interface FindawayDownloadEngineType {

  /**
   * An observable that shows all download engine events.
   */

  val events: Observable<DownloadEvent>

  /**
   * Start a download. Return an observable that publishes events about the download.
   */

  fun download(request: DownloadRequest): Observable<DownloadEvent>

  /**
   * Cancel a download.
   */

  fun pause(request: DownloadRequest)

  /**
   * Delete downloaded data.
   */

  fun delete(request: DownloadRequest)

  fun status(contentId: String, part: Int, chapter: Int): Observable<DownloadStatus>
}
