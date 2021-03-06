package org.librarysimplified.audiobook.tests

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.manifest_fulfill.api.ManifestFulfillmentStrategies
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicType
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyProviderType

abstract class ManifestFulfillmentStrategiesContract {

  @Test
  fun testBasic0() {
    val strategy =
      ManifestFulfillmentStrategies.findStrategy(ManifestFulfillmentBasicType::class.java)
    Assertions.assertNotNull(strategy)
  }

  @Test
  fun testBasic1() {
    val strategy =
      ManifestFulfillmentStrategies.findStrategy(ManifestFulfillmentStrategyProviderType::class.java)
    Assertions.assertNotNull(strategy)
  }
}
