package org.librarysimplified.audiobook.license_check.api

import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckProviderType
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.http.api.LSHTTPClientType
import java.io.File

/**
 * The parameters for a set of license checks.
 */

data class LicenseCheckParameters(

  /**
   * The manifest upon which the license checks will be evaluated.
   */

  val manifest: PlayerManifest,

  /**
   * The HTTP client used to make HTTP requests.
   */

  val httpClient: LSHTTPClientType,

  /**
   * The list of license checks that will be evaluated.
   */

  val checks: List<SingleLicenseCheckProviderType>,

  /**
   * The directory in which to store cache files.
   */

  val cacheDirectory: File
)
