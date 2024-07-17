package org.librarysimplified.audiobook.demo

import android.content.Context
import org.librarysimplified.audiobook.json_web_token.JSONBase64String
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAPassword
import org.xmlpull.v1.XmlPullParser
import java.net.URI
import java.text.ParseException

/**
 * Bundled presets for testing various audio books.
 */

data class ExamplePreset(
  val name: String,
  val uri: URI,
  val type: ExampleTargetType,
  val credentials: ExamplePlayerCredentials,
  val lcpPassphrase: String?
) {

  companion object {

    /**
     * Parse bundled repositories from XML resources.
     */

    fun fromXMLResources(context: Context): List<ExamplePreset> {
      return loadFrom(context.resources.getXml(R.xml.presets))
    }

    /**
     * Load presets from the given XML parser.
     */

    fun loadFrom(
      parser: XmlPullParser
    ): List<ExamplePreset> {
      var name = ""
      var location = ""
      var lcpPassphrase: String? = null
      var type = ExampleTargetType.MANIFEST
      var credentials = ExamplePlayerCredentials.None(0) as ExamplePlayerCredentials
      val presets = mutableListOf<ExamplePreset>()

      while (true) {
        when (parser.next()) {
          XmlPullParser.END_DOCUMENT ->
            return presets.toList()

          XmlPullParser.START_TAG ->
            when (parser.name) {
              "Presets" -> {
              }

              "LCPPassphrase" -> {
                lcpPassphrase = parser.getAttributeValue(null, "value")
              }

              "Preset" -> {
                lcpPassphrase = null
                name = parser.getAttributeValue(null, "name")
                location = parser.getAttributeValue(null, "location")
                type = parseType(parser.getAttributeValue(null, "type"))
              }

              "AuthenticationBasic" -> {
                credentials = ExamplePlayerCredentials.Basic(
                  userName = parser.getAttributeValue(null, "userName"),
                  password = parser.getAttributeValue(null, "password")
                )
              }

              "Overdrive" -> {
                credentials = ExamplePlayerCredentials.Overdrive(
                  userName = parser.getAttributeValue(null, "userName"),
                  password = OPAPassword.Password(parser.getAttributeValue(null, "password")),
                  clientKey = parser.getAttributeValue(null, "clientKey"),
                  clientPass = parser.getAttributeValue(null, "clientSecret")
                )
              }

              "Feedbooks" -> {
                val encoded =
                  parser.getAttributeValue(null, "bearerTokenSecret")

                credentials = ExamplePlayerCredentials.Feedbooks(
                  userName = parser.getAttributeValue(null, "userName"),
                  password = parser.getAttributeValue(null, "password"),
                  bearerTokenSecret = JSONBase64String(encoded).decode(),
                  issuerURL = parser.getAttributeValue(null, "issuerURL")
                )
              }

              "AuthenticationNone" -> {
                credentials = ExamplePlayerCredentials.None(0)
              }

              else -> {
              }
            }

          XmlPullParser.END_TAG -> {
            when (parser.name) {
              "Presets" -> Unit
              "Preset" -> {
                presets.add(
                  ExamplePreset(
                    name = name,
                    uri = URI.create(location),
                    credentials = credentials,
                    type = type,
                    lcpPassphrase = lcpPassphrase
                  )
                )
              }
              else -> {
              }
            }
          }

          else -> Unit
        }
      }
    }

    private fun parseType(
      value: String?
    ): ExampleTargetType {
      return when (value) {
        null -> {
          ExampleTargetType.MANIFEST
        }
        "manifest" -> {
          ExampleTargetType.MANIFEST
        }
        "lcpLicense" -> {
          ExampleTargetType.LCP_LICENSE
        }
        else -> {
          throw ParseException("Unrecognized preset type: $value", 0)
        }
      }
    }
  }
}
