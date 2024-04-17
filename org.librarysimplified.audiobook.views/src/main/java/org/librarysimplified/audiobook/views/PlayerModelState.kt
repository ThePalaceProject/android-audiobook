package org.librarysimplified.audiobook.views

import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.librarysimplified.audiobook.parser.api.ParseError

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
    val manifest: PlayerManifest
  ) : PlayerModelState()

  data class PlayerBookOpenFailed(
    val message: String
  ) : PlayerModelState()

  data class PlayerOpen(
    val player: PlayerBookAndPlayer
  ) : PlayerModelState()
}
