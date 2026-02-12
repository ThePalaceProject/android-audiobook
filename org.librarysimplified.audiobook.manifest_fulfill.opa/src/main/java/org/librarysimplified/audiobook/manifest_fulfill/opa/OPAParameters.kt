package org.librarysimplified.audiobook.manifest_fulfill.opa

import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyParametersType

/**
 * Parameters for Overdrive Patron Authentication.
 *
 * @see "https://developer.overdrive.com/apis/patron-auth"
 */

data class OPAParameters(
  /**
   * The authorization handler.
   */

  val authorizationHandler: PlayerAuthorizationHandlerType,

  /**
   * The client key that is baked into the application.
   */

  val clientKey: String?,

  /**
   * The client password that is baked into the application.
   */

  val clientPass: String?,

  /**
   * The target URI of the manifest.
   */

  val targetURI: OPAManifestURI,
  override val userAgent: PlayerUserAgent
) : ManifestFulfillmentStrategyParametersType
