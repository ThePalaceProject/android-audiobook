package org.librarysimplified.audiobook.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class PlayerFragment2 : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    state: Bundle?
  ): View {
    return inflater.inflate(R.layout.player_view, container, false)
  }

  override fun onStart() {
    super.onStart()
  }

  override fun onStop() {
    super.onStop()
  }
}
