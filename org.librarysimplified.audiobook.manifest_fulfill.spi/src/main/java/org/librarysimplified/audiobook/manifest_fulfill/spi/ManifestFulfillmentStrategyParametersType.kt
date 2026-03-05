package org.librarysimplified.audiobook.manifest_fulfill.spi

import org.librarysimplified.http.api.LSHTTPClientType

/**
 * The base type of manifest fulfillment strategy parameters.
 */

interface ManifestFulfillmentStrategyParametersType {

  /**
   * The client used to make various HTTP requests.
   */

  val httpClient: LSHTTPClientType
}
