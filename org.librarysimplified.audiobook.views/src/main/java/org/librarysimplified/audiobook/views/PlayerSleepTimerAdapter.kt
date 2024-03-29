package org.librarysimplified.audiobook.views

import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfigurationPreset
import org.librarysimplified.audiobook.api.PlayerUIThread

/**
 * A Recycler view adapter used to display and control a sleep timer configuration menu.
 */

class PlayerSleepTimerAdapter(
  private val context: Context,
  private val rates: List<PlayerSleepTimerConfigurationPreset>,
  private val onSelect: (PlayerSleepTimerConfigurationPreset) -> Unit
) :
  RecyclerView.Adapter<PlayerSleepTimerAdapter.ViewHolder>() {

  private val listener: View.OnClickListener = View.OnClickListener { v ->
    this.onSelect(v.tag as PlayerSleepTimerConfigurationPreset)
  }

  companion object {

    fun textOfConfiguration(
      resources: Resources,
      item: PlayerSleepTimerConfigurationPreset
    ): String {
      return when (item) {
        PlayerSleepTimerConfigurationPreset.END_OF_CHAPTER ->
          resources.getString(R.string.audiobook_player_sleep_end_of_chapter)
        PlayerSleepTimerConfigurationPreset.MINUTES_60 ->
          resources.getString(R.string.audiobook_player_sleep_60)
        PlayerSleepTimerConfigurationPreset.MINUTES_45 ->
          resources.getString(R.string.audiobook_player_sleep_45)
        PlayerSleepTimerConfigurationPreset.MINUTES_30 ->
          resources.getString(R.string.audiobook_player_sleep_30)
        PlayerSleepTimerConfigurationPreset.MINUTES_15 ->
          resources.getString(R.string.audiobook_player_sleep_15)
        PlayerSleepTimerConfigurationPreset.NOW ->
          resources.getString(R.string.audiobook_player_sleep_now)
        PlayerSleepTimerConfigurationPreset.OFF ->
          resources.getString(R.string.audiobook_player_sleep_off)
      }
    }

    private fun menuItemContentDescription(
      resources: Resources,
      item: PlayerSleepTimerConfigurationPreset
    ): String {
      return when (item) {
        PlayerSleepTimerConfigurationPreset.OFF ->
          resources.getString(R.string.audiobook_accessibility_menu_sleep_timer_item_off)
        PlayerSleepTimerConfigurationPreset.END_OF_CHAPTER ->
          resources.getString(R.string.audiobook_accessibility_menu_sleep_timer_item_at_end_of_chapter)
        PlayerSleepTimerConfigurationPreset.MINUTES_60 ->
          resources.getString(R.string.audiobook_accessibility_menu_sleep_timer_item_in_60_minutes)
        PlayerSleepTimerConfigurationPreset.MINUTES_45 ->
          resources.getString(R.string.audiobook_accessibility_menu_sleep_timer_item_in_45_minutes)
        PlayerSleepTimerConfigurationPreset.MINUTES_30 ->
          resources.getString(R.string.audiobook_accessibility_menu_sleep_timer_item_in_30_minutes)
        PlayerSleepTimerConfigurationPreset.MINUTES_15 ->
          resources.getString(R.string.audiobook_accessibility_menu_sleep_timer_item_in_15_minutes)
        PlayerSleepTimerConfigurationPreset.NOW ->
          resources.getString(R.string.audiobook_accessibility_menu_sleep_timer_item_now)
      }
    }

    fun hasBeenSetToContentDescriptionOf(
      resources: Resources,
      item: PlayerSleepTimerConfigurationPreset
    ): String {
      return StringBuilder(64)
        .append(resources.getString(R.string.audiobook_accessibility_sleep_timer_has_been_set))
        .append(" ")
        .append(menuItemContentDescription(resources, item))
        .toString()
    }
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): ViewHolder {
    PlayerUIThread.checkIsUIThread()

    val view =
      LayoutInflater.from(parent.context)
        .inflate(R.layout.player_sleep_item_view, parent, false)

    return this.ViewHolder(view)
  }

  override fun onBindViewHolder(
    holder: ViewHolder,
    position: Int
  ) {
    PlayerUIThread.checkIsUIThread()

    val item = this.rates[position]
    holder.text.text =
      textOfConfiguration(resources = this.context.resources, item = item)
    holder.view.contentDescription =
      menuItemContentDescription(resources = this.context.resources, item = item)

    val view = holder.view
    view.tag = item
    view.setOnClickListener(this@PlayerSleepTimerAdapter.listener)
  }

  override fun getItemCount(): Int = this.rates.size

  inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
    val text: TextView = view.findViewById(R.id.player_sleep_item_view_name)
  }
}
