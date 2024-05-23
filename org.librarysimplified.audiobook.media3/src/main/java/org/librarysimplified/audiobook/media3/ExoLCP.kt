package org.librarysimplified.audiobook.media3

import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.media3.ExoEngineProvider.Companion.LCP_SCHEME

/**
 * Functions to infer LCP encryption from manifests.
 */

object ExoLCP {

  /**
   * @return `true` if the given manifest implies LCP encryption is used
   */

  fun isLCP(
    manifest: PlayerManifest
  ): Boolean {
    return manifest.readingOrder.any { item -> this.isLCPLink(item.link) }
  }

  private fun isLCPLink(
    link: PlayerManifestLink.LinkBasic
  ): Boolean {
    val enc = link.properties.encrypted
    if (enc != null) {
      return enc.scheme == LCP_SCHEME
    }
    return false
  }
}
