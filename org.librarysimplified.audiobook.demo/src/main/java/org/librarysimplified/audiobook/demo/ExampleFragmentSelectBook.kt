package org.librarysimplified.audiobook.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.librarysimplified.audiobook.api.PlayerBookCredentialsLCP
import org.librarysimplified.audiobook.api.PlayerBookCredentialsNone
import org.librarysimplified.audiobook.api.PlayerBookCredentialsType
import org.librarysimplified.audiobook.json_web_token.JSONBase64String
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckProviderType
import org.librarysimplified.audiobook.manifest_fulfill.api.ManifestFulfillmentStrategies
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicCredentials
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicParameters
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicType
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAManifestFulfillmentStrategyProviderType
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAManifestURI
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAParameters
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAPassword
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.views.PlayerModel
import java.io.File
import java.net.URI
import java.util.ServiceLoader

class ExampleFragmentSelectBook : Fragment(R.layout.example_config_screen) {

  private lateinit var authBasic: String
  private lateinit var authFeedbooks: String
  private lateinit var authItems: Array<String>
  private lateinit var authNone: String
  private lateinit var authOverdrive: String
  private lateinit var authentication: Spinner
  private lateinit var authenticationBasic: ViewGroup
  private lateinit var authenticationBasicPassword: TextView
  private lateinit var authenticationBasicUser: TextView
  private lateinit var authenticationFeedbooks: ViewGroup
  private lateinit var authenticationFeedbooksIssuer: TextView
  private lateinit var authenticationFeedbooksPassword: TextView
  private lateinit var authenticationFeedbooksSecret: TextView
  private lateinit var authenticationFeedbooksUser: TextView
  private lateinit var authenticationOverdrive: ViewGroup
  private lateinit var authenticationOverdriveClientKey: TextView
  private lateinit var authenticationOverdriveClientSecret: TextView
  private lateinit var authenticationOverdrivePassword: TextView
  private lateinit var authenticationOverdriveUser: TextView
  private lateinit var authenticationSelected: String
  private lateinit var lcpPassphrase: EditText
  private lateinit var location: TextView
  private lateinit var play: Button
  private lateinit var presets: Spinner
  private lateinit var stream: CheckBox
  private lateinit var typeLCP: String
  private lateinit var typeManifest: String
  private lateinit var typeSelect: Spinner
  private lateinit var typeSelected: String
  private lateinit var types: Array<String>

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val layout =
      inflater.inflate(R.layout.example_config_screen, container, false)

    this.typeManifest =
      this.getString(R.string.exManifest)
    this.typeLCP =
      this.getString(R.string.exLCPLicense)

    this.authNone =
      this.getString(R.string.exAuthNone)
    this.authBasic =
      this.getString(R.string.exAuthBasic)
    this.authFeedbooks =
      this.getString(R.string.exAuthBasicFeedbooks)
    this.authOverdrive =
      this.getString(R.string.exAuthBasicOverdrive)
    this.authItems =
      this.resources.getStringArray(R.array.exAuthenticationTypes)
    this.types =
      this.resources.getStringArray(R.array.exTargetTypes)

    this.authenticationBasic =
      layout.findViewById(R.id.exAuthenticationBasicParameters)
    this.authenticationBasicUser =
      this.authenticationBasic.findViewById(R.id.exAuthenticationBasicUser)
    this.authenticationBasicPassword =
      this.authenticationBasic.findViewById(R.id.exAuthenticationBasicPassword)

    this.authenticationFeedbooks =
      layout.findViewById(R.id.exAuthenticationFeedbooksParameters)
    this.authenticationFeedbooksUser =
      this.authenticationFeedbooks.findViewById(R.id.exAuthenticationFeedbooksUser)
    this.authenticationFeedbooksPassword =
      this.authenticationFeedbooks.findViewById(R.id.exAuthenticationFeedbooksPassword)
    this.authenticationFeedbooksIssuer =
      this.authenticationFeedbooks.findViewById(R.id.exAuthenticationFeedbooksIssuer)
    this.authenticationFeedbooksSecret =
      this.authenticationFeedbooks.findViewById(R.id.exAuthenticationFeedbooksSecret)

    this.authenticationOverdrive =
      layout.findViewById(R.id.exAuthenticationOverdriveParameters)
    this.authenticationOverdriveUser =
      this.authenticationOverdrive.findViewById(R.id.exAuthenticationOverdriveUser)
    this.authenticationOverdrivePassword =
      this.authenticationOverdrive.findViewById(R.id.exAuthenticationOverdrivePassword)
    this.authenticationOverdriveClientKey =
      this.authenticationOverdrive.findViewById(R.id.exAuthenticationOverdriveClientKey)
    this.authenticationOverdriveClientSecret =
      this.authenticationOverdrive.findViewById(R.id.exAuthenticationOverdriveClientSecret)

    this.stream =
      layout.findViewById(R.id.exStream)
    this.typeSelect =
      layout.findViewById(R.id.exTypeSelection)
    this.authentication =
      layout.findViewById(R.id.exAuthenticationSelection)

    this.authentication.adapter =
      ArrayAdapter.createFromResource(
        ExampleApplication.application,
        R.array.exAuthenticationTypes,
        android.R.layout.simple_list_item_1
      )
    this.typeSelect.adapter =
      ArrayAdapter.createFromResource(
        ExampleApplication.application, R.array.exTargetTypes, android.R.layout.simple_list_item_1
      )

    this.play =
      layout.findViewById(R.id.exPlay)

    this.location =
      layout.findViewById(R.id.exLocation)
    this.presets =
      layout.findViewById(R.id.exPresets)
    this.lcpPassphrase =
      layout.findViewById(R.id.exLCPPassphrase)

    this.onSelectedAuthentication(this.authNone)
    this.onSelectedType(this.typeManifest)
    return layout
  }

  override fun onStart() {
    super.onStart()

    this.stream.setOnCheckedChangeListener { _, isChecked ->
      PlayerModel.setStreamingPermitted(isChecked)
    }
    PlayerModel.setStreamingPermitted(this.stream.isChecked)

    this.authentication.onItemSelectedListener =
      object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {
          // Nothing to do
        }

        override fun onItemSelected(
          parent: AdapterView<*>?,
          view: View?,
          position: Int,
          id: Long
        ) {
          this@ExampleFragmentSelectBook.onSelectedAuthentication(
            this@ExampleFragmentSelectBook.authentication.getItemAtPosition(
              position
            ) as String
          )
        }
      }

    this.typeSelect.onItemSelectedListener =
      object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {
          // Nothing to do
        }

        override fun onItemSelected(
          parent: AdapterView<*>?,
          view: View?,
          position: Int,
          id: Long
        ) {
          this@ExampleFragmentSelectBook.onSelectedType(
            this@ExampleFragmentSelectBook.typeSelect.getItemAtPosition(
              position
            ) as String
          )
        }
      }

    val presetList =
      ExamplePreset.fromXMLResources(this.requireContext())
    val presetAdapter =
      ArrayAdapter(
        this.requireContext(),
        android.R.layout.simple_list_item_1,
        presetList.map { p -> p.name }.toTypedArray()
      )

    this.presets.adapter = presetAdapter
    this.presets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) {
      }

      override fun onItemSelected(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
      ) {
        this@ExampleFragmentSelectBook.onSelectedPreset(presetList[position])
      }
    }

    this.play.setOnClickListener {
      this.onSelectedPlay()
    }
  }

  private fun onSelectedPlay() {
    val credentials =
      when (this.authenticationSelected) {
        this.authBasic -> {
          ExamplePlayerCredentials.Basic(
            userName = this.authenticationBasicUser.text.toString(),
            password = this.authenticationBasicPassword.text.toString()
          )
        }

        this.authNone -> {
          ExamplePlayerCredentials.None(0)
        }

        this.authFeedbooks -> {
          val secretText =
            this.authenticationFeedbooksSecret.text.toString()
          val decoded =
            JSONBase64String(secretText).decode()

          ExamplePlayerCredentials.Feedbooks(
            userName = this.authenticationFeedbooksUser.text.toString(),
            password = this.authenticationFeedbooksPassword.text.toString(),
            issuerURL = this.authenticationFeedbooksIssuer.text.toString(),
            bearerTokenSecret = decoded
          )
        }

        this.authOverdrive -> {
          ExamplePlayerCredentials.Overdrive(
            userName = this.authenticationOverdriveUser.text.toString(),
            password = OPAPassword.Password(this.authenticationOverdrivePassword.text.toString()),
            clientKey = this.authenticationOverdriveClientKey.text.toString(),
            clientPass = this.authenticationOverdriveClientSecret.text.toString()
          )
        }

        else -> {
          throw UnsupportedOperationException()
        }
      }

    val sourceURI = URI.create(this.location.text.toString())

    when (this.typeSelected) {
      this.typeLCP -> {
        PlayerModel.downloadParseAndCheckLCPLicense(
          context = ExampleApplication.application,
          cacheDir = ExampleApplication.application.cacheDir,
          userAgent = ExampleApplication.userAgent,
          licenseChecks = ServiceLoader.load(SingleLicenseCheckProviderType::class.java).toList(),
          licenseParameters = this.basicParametersForLCPLicense(sourceURI, credentials),
          parserExtensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList(),
          bookFile = File(ExampleApplication.application.cacheDir, "lcpBook.audiobook"),
          bookFileTemp = File(ExampleApplication.application.cacheDir, "lcpBook.audiobook.tmp"),
          licenseFile = File(ExampleApplication.application.cacheDir, "lcpBook.lcpl"),
          licenseFileTemp = File(ExampleApplication.application.cacheDir, "lcpBook.lcpl.tmp"),
          bookCredentials = this.bookCredentials()
        )
      }

      this.typeManifest -> {
        PlayerModel.downloadParseAndCheckManifest(
          sourceURI = sourceURI,
          cacheDir = ExampleApplication.application.cacheDir,
          userAgent = ExampleApplication.userAgent,
          licenseChecks = ServiceLoader.load(SingleLicenseCheckProviderType::class.java).toList(),
          strategy = this.downloadStrategyForCredentials(sourceURI, credentials),
          parserExtensions = ServiceLoader.load(ManifestParserExtensionType::class.java).toList(),
          bookCredentials = this.bookCredentials()
        )
      }
    }
  }

  private fun bookCredentials(): PlayerBookCredentialsType {
    return when (val text = this.lcpPassphrase.text.trim().toString()) {
      "" -> PlayerBookCredentialsNone
      else -> PlayerBookCredentialsLCP(text)
    }
  }

  private fun basicParametersForLCPLicense(
    sourceURI: URI,
    credentials: ExamplePlayerCredentials
  ): ManifestFulfillmentBasicParameters {
    return when (credentials) {
      is ExamplePlayerCredentials.Basic -> {
        ManifestFulfillmentBasicParameters(
          uri = sourceURI,
          credentials = ManifestFulfillmentBasicCredentials(
            userName = credentials.userName,
            password = credentials.password
          ),
          httpClient = ExampleApplication.httpClient,
          userAgent = ExampleApplication.userAgent
        )
      }

      is ExamplePlayerCredentials.Feedbooks -> {
        throw IllegalStateException()
      }

      is ExamplePlayerCredentials.None -> {
        ManifestFulfillmentBasicParameters(
          uri = sourceURI,
          credentials = null,
          httpClient = ExampleApplication.httpClient,
          userAgent = ExampleApplication.userAgent
        )
      }

      is ExamplePlayerCredentials.Overdrive -> {
        throw IllegalStateException()
      }
    }
  }

  private fun onSelectedType(type: String) {
    this.typeSelected = type
    this.typeSelect.setSelection(this.types.indexOf(type))
  }

  private fun onSelectedAuthentication(authentication: String) {
    this.authenticationSelected = authentication
    this.authentication.setSelection(this.authItems.indexOf(authentication))

    return when (authentication) {
      this.authFeedbooks -> {
        this.authenticationBasic.visibility = View.GONE
        this.authenticationOverdrive.visibility = View.GONE
        this.authenticationFeedbooks.visibility = View.VISIBLE
      }

      this.authBasic -> {
        this.authenticationBasic.visibility = View.VISIBLE
        this.authenticationOverdrive.visibility = View.GONE
        this.authenticationFeedbooks.visibility = View.GONE
      }

      this.authNone -> {
        this.authenticationBasic.visibility = View.GONE
        this.authenticationOverdrive.visibility = View.GONE
        this.authenticationFeedbooks.visibility = View.GONE
      }

      this.authOverdrive -> {
        this.authenticationBasic.visibility = View.GONE
        this.authenticationOverdrive.visibility = View.VISIBLE
        this.authenticationFeedbooks.visibility = View.GONE
      }

      else -> {
        throw UnsupportedOperationException()
      }
    }
  }

  private fun onSelectedPreset(preset: ExamplePreset) {
    this.location.text = preset.uri.toString()
    this.lcpPassphrase.setText(preset.lcpPassphrase ?: this.lcpPassphrase.text.toString())

    when (preset.type) {
      ExampleTargetType.MANIFEST -> {
        this.onSelectedType(this.typeManifest)
      }

      ExampleTargetType.LCP_LICENSE -> {
        this.onSelectedType(this.typeLCP)
      }
    }

    return when (val credentials = preset.credentials) {
      is ExamplePlayerCredentials.None -> {
        this.onSelectedAuthentication(this.authNone)
      }

      is ExamplePlayerCredentials.Basic -> {
        this.onSelectedAuthentication(this.authBasic)
        this.authenticationBasicUser.text = credentials.userName
        this.authenticationBasicPassword.text = credentials.password
      }

      is ExamplePlayerCredentials.Feedbooks -> {
        this.onSelectedAuthentication(this.authFeedbooks)

        val encoded = JSONBase64String.encode(credentials.bearerTokenSecret)
        this.authenticationFeedbooksUser.text = credentials.userName
        this.authenticationFeedbooksPassword.text = credentials.password
        this.authenticationFeedbooksIssuer.text = credentials.issuerURL
        this.authenticationFeedbooksSecret.text = encoded.text
      }

      is ExamplePlayerCredentials.Overdrive -> {
        this.onSelectedAuthentication(this.authOverdrive)
        this.authenticationOverdriveUser.text = credentials.userName
        this.authenticationOverdrivePassword.text =
          when (val pass = credentials.password) {
            OPAPassword.NotRequired -> ""
            is OPAPassword.Password -> pass.password
          }
        this.authenticationOverdriveClientSecret.text = credentials.clientPass
        this.authenticationOverdriveClientKey.text = credentials.clientKey
      }
    }
  }

  private fun downloadStrategyForCredentials(
    source: URI,
    credentials: ExamplePlayerCredentials
  ): ManifestFulfillmentStrategyType {
    return when (credentials) {
      is ExamplePlayerCredentials.None -> {
        val strategies =
          ManifestFulfillmentStrategies.findStrategy(ManifestFulfillmentBasicType::class.java)
            ?: throw UnsupportedOperationException()

        strategies.create(
          ManifestFulfillmentBasicParameters(
            uri = source,
            credentials = null,
            httpClient = ExampleApplication.httpClient,
            userAgent = ExampleApplication.userAgent
          )
        )
      }

      is ExamplePlayerCredentials.Basic -> {
        val strategies =
          ManifestFulfillmentStrategies.findStrategy(ManifestFulfillmentBasicType::class.java)
            ?: throw UnsupportedOperationException()

        val credentials =
          ManifestFulfillmentBasicCredentials(
            userName = credentials.userName,
            password = credentials.password
          )

        strategies.create(
          ManifestFulfillmentBasicParameters(
            uri = source,
            credentials = credentials,
            httpClient = ExampleApplication.httpClient,
            userAgent = ExampleApplication.userAgent
          )
        )
      }

      is ExamplePlayerCredentials.Feedbooks -> {
        val strategies =
          ManifestFulfillmentStrategies.findStrategy(ManifestFulfillmentBasicType::class.java)
            ?: throw UnsupportedOperationException()

        val credentials =
          ManifestFulfillmentBasicCredentials(
            userName = credentials.userName,
            password = credentials.password
          )

        strategies.create(
          ManifestFulfillmentBasicParameters(
            uri = source,
            credentials = credentials,
            httpClient = ExampleApplication.httpClient,
            userAgent = ExampleApplication.userAgent
          )
        )
      }

      is ExamplePlayerCredentials.Overdrive -> {
        val strategies =
          ManifestFulfillmentStrategies.findStrategy(
            OPAManifestFulfillmentStrategyProviderType::class.java
          ) ?: throw UnsupportedOperationException()

        strategies.create(
          OPAParameters(
            userName = credentials.userName,
            password = credentials.password,
            clientKey = credentials.clientKey,
            clientPass = credentials.clientPass,
            targetURI = OPAManifestURI.Indirect(source),
            userAgent = ExampleApplication.userAgent
          )
        )
      }
    }
  }
}
