package org.librarysimplified.audiobook.api

sealed class PlayerDownloadTaskStatus {

  data object IdleNotDownloaded : PlayerDownloadTaskStatus()

  data object IdleDownloaded : PlayerDownloadTaskStatus()

  data class Failed(
    val message: String,
    val exception: Exception?
  ) : PlayerDownloadTaskStatus()

  data class Downloading(
    val progress: Double?
  ) : PlayerDownloadTaskStatus()
}
