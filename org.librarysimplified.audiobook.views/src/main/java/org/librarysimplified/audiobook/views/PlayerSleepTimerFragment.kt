package org.librarysimplified.audiobook.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerSleepTimer
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.WithDuration
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfigurationPreset
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfigurationPreset.END_OF_CHAPTER
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfigurationPreset.MINUTES_15
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfigurationPreset.MINUTES_30
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfigurationPreset.MINUTES_45
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfigurationPreset.MINUTES_60
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfigurationPreset.NOW
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfigurationPreset.OFF
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.slf4j.LoggerFactory

/**
 * A sleep timer configuration fragment.
 */

class PlayerSleepTimerFragment : DialogFragment() {

  private val log =
    LoggerFactory.getLogger(PlayerSleepTimerFragment::class.java)

  private lateinit var list: RecyclerView
  private lateinit var adapter: PlayerSleepTimerAdapter

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    state: Bundle?
  ): View {
    val view: ViewGroup =
      inflater.inflate(R.layout.player_sleep_timer_view, container, false) as ViewGroup

    this.list = view.findViewById(R.id.list)
    this.list.layoutManager = LinearLayoutManager(view.context)
    this.list.setHasFixedSize(true)

    val cancelButton: TextView = view.findViewById(R.id.cancel_button)
    cancelButton.setOnClickListener { this.dismiss() }
    return view
  }

  override fun onStart() {
    super.onStart()

    this.adapter =
      PlayerSleepTimerAdapter(
        context = this.requireContext(),
        rates = this.enabledSleepTimerConfigurations(),
        onSelect = { item -> this.onSleepTimerSelected(item) }
      )
    this.list.adapter = this.adapter
  }

  /**
   * Retrieve a list of all of the enabled sleep timer configurations. Some options may or may not
   * be present based on various debugging related properties.
   */

  private fun enabledSleepTimerConfigurations(): List<PlayerSleepTimerConfigurationPreset> {
    val nowEnabled =
      this.requireContext()
        .resources.getBoolean(R.bool.audiobook_player_debug_sleep_timer_now_enabled)

    return PlayerSleepTimerConfigurationPreset.entries.filter { configuration ->
      when (configuration) {
        MINUTES_15,
        MINUTES_30,
        MINUTES_45,
        MINUTES_60,
        OFF,
        END_OF_CHAPTER -> true
        NOW -> nowEnabled
      }
    }
  }

  private fun onSleepTimerSelected(item: PlayerSleepTimerConfigurationPreset) {
    this.log.debug("onSleepTimerSelected: {}", item)

    PlayerSleepTimer.configure(
      when (item) {
        NOW -> WithDuration(Duration.standardSeconds(1))
        OFF -> PlayerSleepTimerConfiguration.Off
        MINUTES_15 -> WithDuration(Duration.standardMinutes(15))
        MINUTES_30 -> WithDuration(Duration.standardMinutes(30))
        MINUTES_45 -> WithDuration(Duration.standardMinutes(45))
        MINUTES_60 -> WithDuration(Duration.standardMinutes(60))
        END_OF_CHAPTER -> PlayerSleepTimerConfiguration.EndOfChapter
      }
    )

    PlayerUIThread.runOnUIThreadDelayed({ this.dismiss() }, 250L)
  }
}
