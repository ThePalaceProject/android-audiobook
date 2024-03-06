package org.librarysimplified.audiobook.tests

import org.librarysimplified.audiobook.lcp.license_status.LicenseStatusParserProviderType
import org.librarysimplified.audiobook.lcp.license_status.LicenseStatusParsers

class LicenseStatusParserTest : LicenseStatusParserContract() {
  override fun parsers(): LicenseStatusParserProviderType {
    return LicenseStatusParsers
  }
}
