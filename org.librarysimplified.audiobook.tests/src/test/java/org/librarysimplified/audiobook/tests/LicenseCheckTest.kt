package org.librarysimplified.audiobook.tests

import org.librarysimplified.audiobook.license_check.api.LicenseCheckProviderType
import org.librarysimplified.audiobook.license_check.api.LicenseChecks
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LicenseCheckTest : LicenseCheckContract() {

  override fun log(): Logger {
    return LoggerFactory.getLogger(LicenseCheckTest::class.java)
  }

  override fun licenseChecks(): LicenseCheckProviderType {
    return LicenseChecks
  }
}
