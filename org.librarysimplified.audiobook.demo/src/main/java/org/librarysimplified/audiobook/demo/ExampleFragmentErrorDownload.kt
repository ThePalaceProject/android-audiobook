package org.librarysimplified.audiobook.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.views.PlayerModel
import java.io.PrintWriter
import java.io.StringWriter

class ExampleFragmentErrorDownload : Fragment() {

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

    this.errorLog.setHorizontallyScrolling(true)

    try {
      val book = PlayerModel.book()
      for (e in book.downloadTasks) {
        val status = e.status
        if (status is PlayerDownloadTaskStatus.Failed) {
          this.errorLog.append("Download of item ${e.playbackURI} failed.\n")
          this.errorLog.append("  ")
          this.errorLog.append(status.message)
          this.errorLog.append("\n")

          val exception = status.exception
          if (exception != null) {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            status.exception?.printStackTrace(pw)
            this.errorLog.append(sw.toString())
            this.errorLog.append("\n")
          }
        }
      }
    } catch (e: Exception) {
      // Nothing to do
    }
  }
}
