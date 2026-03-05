package org.librarysimplified.audiobook.license_check.spi

import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.http.api.LSHTTPClientType
import java.io.File

/**
 * The parameters for a single license check.
 */

data class SingleLicenseCheckParameters(

  /**
   * The manifest upon which the license check will be evaluated.
   */

  val manifest: PlayerManifest,

  /**
   * The HTTP client used to make HTTP requests.
   */

  val httpClient: LSHTTPClientType,

  /**
   * A function that will receive the results of license checking.
   */

  val onStatusChanged: (SingleLicenseCheckStatus) -> Unit,

  /**
   * The directory in which to store cache files.
   */

  val cacheDirectory: File
)
