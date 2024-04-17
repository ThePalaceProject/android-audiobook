package org.librarysimplified.audiobook.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.slf4j.LoggerFactory

/**
 * A playback rate configuration fragment.
 */

class PlayerPlaybackRateFragment : DialogFragment() {

  private val log =
    LoggerFactory.getLogger(PlayerPlaybackRateFragment::class.java)

  private lateinit var adapter: PlayerPlaybackRateAdapter
  private lateinit var list: RecyclerView

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    state: Bundle?
  ): View {
    val view: ViewGroup =
      inflater.inflate(R.layout.player_rate_view, container, false) as ViewGroup

    this.list = view.findViewById(R.id.list)
    this.list.layoutManager = LinearLayoutManager(view.context)
    this.list.setHasFixedSize(true)

    val cancelButton: TextView = view.findViewById(R.id.cancel_button)
    cancelButton.setOnClickListener { this.dismiss() }
    return view
  }

  override fun onStart() {
    super.onStart()

    this.adapter = PlayerPlaybackRateAdapter(
      resources = this.resources,
      rates = PlayerPlaybackRate.entries.toList(),
      onSelect = this::onPlaybackRateSelected
    )
    this.list.adapter = this.adapter
    this.adapter.setCurrentPlaybackRate(PlayerModel.playbackRate)
  }

  private fun onPlaybackRateSelected(item: PlayerPlaybackRate) {
    this.log.debug("onPlaybackRateSelected: {}", item)

    PlayerModel.setPlaybackRate(item)
    this.adapter.setCurrentPlaybackRate(item)
    PlayerUIThread.runOnUIThreadDelayed({ this.dismiss() }, 250L)
  }
}
