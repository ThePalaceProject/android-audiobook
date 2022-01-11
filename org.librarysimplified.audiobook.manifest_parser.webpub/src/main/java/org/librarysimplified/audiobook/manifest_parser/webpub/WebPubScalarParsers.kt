package org.librarysimplified.audiobook.manifest_parser.webpub

import one.irradia.fieldrush.api.FRParseResult
import one.irradia.fieldrush.api.FRParserObjectMapType
import one.irradia.fieldrush.api.FRValueParserType
import one.irradia.fieldrush.vanilla.FRValueParsers
import org.librarysimplified.audiobook.manifest.api.PlayerManifestScalar
import org.librarysimplified.audiobook.manifest.api.PlayerManifestScalar.PlayerManifestScalarBoolean
import org.librarysimplified.audiobook.manifest.api.PlayerManifestScalar.PlayerManifestScalarNumber.PlayerManifestScalarInteger
import org.librarysimplified.audiobook.manifest.api.PlayerManifestScalar.PlayerManifestScalarNumber.PlayerManifestScalarReal
import org.librarysimplified.audiobook.manifest.api.PlayerManifestScalar.PlayerManifestScalarString

/**
 * A parser that parses objects consisting of string keys with scalar values.
 */

object WebPubScalarParsers {

  private fun textToScalar(
    text: String
  ): FRParseResult<PlayerManifestScalar> {
    return FRParseResult.succeed(
      if (text.startsWith("0")) {
        // If the text begins with "0", parse it into a string to ensure that the leading "0" is retained.
        PlayerManifestScalarString(text)
      }
      else {
        when (val integer = text.toIntOrNull()) {
          null ->
            when (val double = text.toDoubleOrNull()) {
              null ->
                when (text) {
                  "true" ->
                    PlayerManifestScalarBoolean(true)
                  "false" ->
                    PlayerManifestScalarBoolean(false)
                  else ->
                    PlayerManifestScalarString(text)
                }
              else ->
                PlayerManifestScalarReal(double)
            }
          else ->
            PlayerManifestScalarInteger(integer)
        }
      }
    )
  }

  fun forManifestScalar(
    receiver: (PlayerManifestScalar) -> Unit = FRValueParsers.ignoringReceiver()
  ): FRValueParserType<PlayerManifestScalar> {
    return FRValueParsers.forScalar(
      validator = ::textToScalar,
      receiver = receiver
    )
  }

  fun forMap(
    receiver: (Map<String, PlayerManifestScalar>) -> Unit = FRValueParsers.ignoringReceiver()
  ): FRParserObjectMapType<PlayerManifestScalar> {
    return FRValueParsers.forObjectMap(
      forKey = { forManifestScalar() },
      receiver = receiver
    )
  }
}
