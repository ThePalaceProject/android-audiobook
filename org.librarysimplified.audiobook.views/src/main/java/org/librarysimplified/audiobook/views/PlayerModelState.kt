package org.librarysimplified.audiobook.views

import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.librarysimplified.audiobook.parser.api.ParseError
import java.io.File
import java.lang.Exception

sealed class PlayerModelState {

  data object PlayerClosed : PlayerModelState()

  data object PlayerManifestInProgress : PlayerModelState()

  data class PlayerManifestDownloadFailed(
    val failure: ManifestFulfillmentErrorType
  ) : PlayerModelState()

  data class PlayerManifestParseFailed(
    val failure: List<ParseError>
  ) : PlayerModelState()

  data object PlayerManifestLicenseChecksFailed : PlayerModelState()

  data class PlayerManifestOK(
    val manifest: PlayerManifest,
    val bookFile: File?
  ) : PlayerModelState()

  data class PlayerBookOpenFailed(
    val message: String,
    val exception: Exception
  ) : PlayerModelState()

  data class PlayerOpen(
    val player: PlayerBookAndPlayer
  ) : PlayerModelState()
}
