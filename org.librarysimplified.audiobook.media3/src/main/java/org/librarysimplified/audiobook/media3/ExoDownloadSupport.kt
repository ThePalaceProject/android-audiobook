package org.librarysimplified.audiobook.media3

import java.net.URI

sealed class ExoDownloadSupport {

  data class DownloadEntireBookAsFile(val targetURI: URI) : ExoDownloadSupport()

  data object DownloadIndividualChaptersAsFiles : ExoDownloadSupport()

  data object DownloadUnsupported : ExoDownloadSupport()
}
