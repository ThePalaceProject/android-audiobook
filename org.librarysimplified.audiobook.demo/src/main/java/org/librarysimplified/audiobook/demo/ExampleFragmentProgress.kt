package org.librarysimplified.audiobook.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvent
import org.librarysimplified.audiobook.views.PlayerModel

class ExampleFragmentProgress : Fragment() {

  private var subscriptions: CompositeDisposable = CompositeDisposable()
  private lateinit var statusMessage: TextView
  private lateinit var progressLog: EditText

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val layout =
      inflater.inflate(R.layout.example_progress_screen, container, false)

    this.progressLog =
      layout.findViewById(R.id.progressLog)
    this.statusMessage =
      layout.findViewById(R.id.progressMessage)

    return layout
  }

  override fun onStart() {
    super.onStart()
    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.manifestDownloadEvents.subscribe(this::onManifestEvent))
    this.subscriptions.add(PlayerModel.singleLicenseCheckEvents.subscribe(this::onLicenseEvent))

    this.populateProgressLog("")
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.dispose()
  }

  private fun onLicenseEvent(
    event: SingleLicenseCheckStatus
  ) {
    this.populateProgressLog(event.message)
  }

  private fun onManifestEvent(
    event: ManifestFulfillmentEvent
  ) {
    this.populateProgressLog(event.message)
  }

  private fun populateProgressLog(lastMessage: String) {
    this.progressLog.text.clear()

    for (e in PlayerModel.manifestDownloadLog) {
      this.progressLog.text.append(e.message + "\n\n")
    }
    for (e in PlayerModel.manifestParseErrorLog) {
      this.progressLog.text.append(e.message + "\n\n")
    }
    for (e in PlayerModel.singleLicenseCheckLog) {
      this.progressLog.text.append(e.source)
      this.progressLog.text.append(": ")
      this.progressLog.text.append(e.message + "\n\n")
    }

    this.progressLog.text.append(lastMessage)
    this.progressLog.text.append("\n")
    this.statusMessage.text = lastMessage
  }
}
