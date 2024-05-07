package org.librarysimplified.audiobook.manifest_fulfill.basic

import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType

/**
 * A provider of basic-auth manifest fulfillment strategies.
 *
 * Note: This class _MUST_ have a public no-arg constructor in order to work with [java.util.ServiceLoader].
 */

class ManifestFulfillmentBasicProvider : ManifestFulfillmentBasicType {

  override fun create(
    configuration: ManifestFulfillmentBasicParameters
  ): ManifestFulfillmentStrategyType {
    return ManifestFulfillmentBasic(
      configuration = configuration
    )
  }
}
