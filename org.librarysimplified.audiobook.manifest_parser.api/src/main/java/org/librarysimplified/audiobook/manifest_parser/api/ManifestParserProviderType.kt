package org.librarysimplified.audiobook.manifest_parser.api

import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.parser.api.ParserProviderType
import org.librarysimplified.audiobook.parser.api.ParserType
import java.io.InputStream
import java.net.URI

/**
 * The type of manifest parser providers.
 */

interface ManifestParserProviderType
  : ParserProviderType<InputStream, ManifestParserExtensionType, PlayerManifest> {

  /**
   * The base format supported by this parser provider.
   */

  val format: String

  /**
   * Return `true` if this parser is capable of parsing the given input.
   */

  fun canParse(
    uri: URI,
    streams: () -> InputStream
  ): Boolean

  /**
   * Create a new parser for the given input.
   */

  override fun createParser(
    uri: URI,
    streams: () -> InputStream,
    extensions: List<ManifestParserExtensionType>,
    warningsAsErrors: Boolean
  ): ParserType<PlayerManifest>

}
