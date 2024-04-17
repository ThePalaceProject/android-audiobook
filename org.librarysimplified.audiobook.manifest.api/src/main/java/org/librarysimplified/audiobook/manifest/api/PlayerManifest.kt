package org.librarysimplified.audiobook.manifest.api

/**
 * A raw audio book manifest, parsed and typed.
 */

data class PlayerManifest(
  val originalBytes: ByteArray,
  val readingOrder: List<PlayerManifestReadingOrderItem>,
  val metadata: PlayerManifestMetadata,
  val links: List<PlayerManifestLink>,
  val extensions: List<PlayerManifestExtensionValueType>,
  val toc: List<PlayerManifestLink>?
)
