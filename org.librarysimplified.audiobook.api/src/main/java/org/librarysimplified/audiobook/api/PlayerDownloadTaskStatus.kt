package org.librarysimplified.audiobook.api

sealed class PlayerDownloadTaskStatus {

  data object IdleNotDownloaded : PlayerDownloadTaskStatus()

  data object IdleDownloaded : PlayerDownloadTaskStatus()

  data object Failed : PlayerDownloadTaskStatus()

  data class Downloading(
    val progress: Double?
  ) : PlayerDownloadTaskStatus()
}
