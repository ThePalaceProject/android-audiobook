package org.librarysimplified.audiobook.views

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration
import org.librarysimplified.audiobook.api.PlayerSleepTimerType
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.views.PlayerAccessibilityEvent.PlayerAccessibilitySleepTimerSettingChanged
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.END_OF_CHAPTER
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.MINUTES_15
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.MINUTES_30
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.MINUTES_45
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.MINUTES_60
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.NOW
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.OFF
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.values
import org.slf4j.LoggerFactory

/**
 * A sleep timer configuration fragment.
 *
 * New instances MUST be created with {@link #newInstance()} rather than calling the constructor
 * directly. The public constructor only exists because the Android API requires it.
 *
 * Activities hosting this fragment MUST implement the {@link org.librarysimplified.audiobook.views.PlayerFragmentListenerType}
 * interface. An exception will be raised if this is not the case.
 */

class PlayerSleepTimerFragment : DialogFragment() {

  private val log = LoggerFactory.getLogger(PlayerSleepTimerFragment::class.java)
  private lateinit var listener: PlayerFragmentListenerType
  private lateinit var adapter: PlayerSleepTimerAdapter
  private lateinit var timer: PlayerSleepTimerType
  private lateinit var player: PlayerType

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    state: Bundle?
  ): View {
    val view: ViewGroup =
      inflater.inflate(R.layout.player_sleep_timer_view, container, false) as ViewGroup

    val list: RecyclerView =
      view.findViewById(R.id.list)
    this.dialog?.setTitle(R.string.audiobook_player_menu_sleep_title)

    list.layoutManager = LinearLayoutManager(view.context)
    list.setHasFixedSize(true)
    list.adapter = this.adapter

    val cancelButton: TextView =
      view.findViewById(R.id.cancel_button)

    cancelButton.setOnClickListener {
      dismiss()
    }

    return view
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)

    if (context is PlayerFragmentListenerType) {
      this.listener = context

      this.player = this.listener.onPlayerWantsPlayer()
      this.timer = this.listener.onPlayerWantsSleepTimer()

      this.adapter =
        PlayerSleepTimerAdapter(
          context = context,
          rates = this.enabledSleepTimerConfigurations(),
          onSelect = { item -> this.onSleepTimerSelected(item) }
        )
    } else {
      throw ClassCastException(
        StringBuilder(64)
          .append("The activity hosting this fragment must implement one or more listener interfaces.\n")
          .append("  Activity: ")
          .append(context::class.java.canonicalName)
          .append('\n')
          .append("  Required interface: ")
          .append(PlayerFragmentListenerType::class.java.canonicalName)
          .append('\n')
          .toString()
      )
    }
  }

  /**
   * Retrieve a list of all of the enabled sleep timer configurations. Some options may or may not
   * be present based on various debugging related properties.
   */

  private fun enabledSleepTimerConfigurations(): List<PlayerSleepTimerConfiguration> {
    val nowEnabled =
      requireContext().resources.getBoolean(R.bool.audiobook_player_debug_sleep_timer_now_enabled)
    return values().toList().filter { configuration ->
      when (configuration) {
        MINUTES_15, MINUTES_30, MINUTES_45, MINUTES_60, OFF, END_OF_CHAPTER -> true
        NOW -> nowEnabled
      }
    }
  }

  private fun onSleepTimerSelected(item: PlayerSleepTimerConfiguration) {
    this.log.debug("onSleepTimerSelected: {}", item)

    try {
      this.listener.onPlayerAccessibilityEvent(
        PlayerAccessibilitySleepTimerSettingChanged(
          PlayerSleepTimerAdapter.hasBeenSetToContentDescriptionOf(resources, item)
        )
      )
      this.listener.onPlayerSleepTimerUpdated(item.duration?.millis)
    } catch (ex: Exception) {
      this.log.debug("ignored exception in event handler: ", ex)
    }

    this.timer.cancel()
    if (item != OFF) {
      this.timer.setDuration(item.duration)
      if (this.player.isPlaying) {
        this.timer.start()
      }
    }
    this.dismiss()
  }

  companion object {
    @JvmStatic
    fun newInstance(): PlayerSleepTimerFragment {
      return PlayerSleepTimerFragment()
    }
  }
}
