package org.librarysimplified.audiobook.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.audiobook.views.PlayerModelState

class ExampleFragmentError : Fragment() {

  private lateinit var errorMessage: TextView
  private lateinit var errorLog: EditText

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val layout =
      inflater.inflate(R.layout.example_error_screen, container, false)

    this.errorLog =
      layout.findViewById(R.id.errorLog)
    this.errorMessage =
      layout.findViewById(R.id.errorMessage)

    return layout
  }

  override fun onStart() {
    super.onStart()

    when (val current = PlayerModel.state) {
      is PlayerModelState.PlayerBookOpenFailed -> {
        this.populateErrorLog(current.message)
        this.errorMessage.text = current.message
      }
      is PlayerModelState.PlayerManifestDownloadFailed -> {
        this.populateErrorLog(current.failure.message)
        this.errorMessage.text = current.failure.message
      }
      PlayerModelState.PlayerManifestLicenseChecksFailed -> {
        this.populateErrorLog("License check failed.")
        this.errorMessage.text = "License check failed."
      }
      is PlayerModelState.PlayerManifestParseFailed -> {
        this.populateErrorLog("Manifest parsing failed.")
        this.errorMessage.text = "Manifest parsing failed."
      }

      PlayerModelState.PlayerClosed,
      PlayerModelState.PlayerManifestInProgress,
      is PlayerModelState.PlayerManifestOK,
      is PlayerModelState.PlayerOpen -> {
        this.errorLog.text.clear()
        this.errorMessage.text = ""
      }
    }
  }

  private fun populateErrorLog(lastMessage: String) {
    this.errorLog.text.clear()

    for (e in PlayerModel.manifestDownloadLog) {
      this.errorLog.text.append(e.message + "\n\n")
    }
    for (e in PlayerModel.manifestParseErrorLog) {
      this.errorLog.text.append(e.message + "\n\n")
    }
    for (e in PlayerModel.singleLicenseCheckLog) {
      this.errorLog.text.append(e.source)
      this.errorLog.text.append(": ")
      this.errorLog.text.append(e.message + "\n\n")
    }

    this.errorLog.text.append(lastMessage)
    this.errorLog.text.append("\n")
  }
}
