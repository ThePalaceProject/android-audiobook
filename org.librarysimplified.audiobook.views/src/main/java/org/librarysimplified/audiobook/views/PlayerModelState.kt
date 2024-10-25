package org.librarysimplified.audiobook.views

import org.librarysimplified.audiobook.api.PlayerBookCredentialsType
import org.librarysimplified.audiobook.api.PlayerBookSource
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentError
import org.librarysimplified.audiobook.parser.api.ParseError

sealed class PlayerModelState {

  data object PlayerClosed : PlayerModelState()

  data object PlayerManifestInProgress : PlayerModelState()

  data class PlayerManifestDownloadFailed(
    val failure: ManifestFulfillmentError
  ) : PlayerModelState()

  data class PlayerManifestParseFailed(
    val failure: List<ParseError>
  ) : PlayerModelState()

  data class PlayerManifestLicenseChecksFailed(
    val messages: List<String>
  ) : PlayerModelState()

  data class PlayerManifestOK(
    val manifest: PlayerManifest,
    val bookSource: PlayerBookSource,
    val bookCredentials: PlayerBookCredentialsType
  ) : PlayerModelState()

  data class PlayerBookOpenFailed(
    val message: String,
    val exception: Exception,
    val extraMessages: List<String>
  ) : PlayerModelState()

  data class PlayerOpen(
    val player: PlayerBookAndPlayer
  ) : PlayerModelState()
}
