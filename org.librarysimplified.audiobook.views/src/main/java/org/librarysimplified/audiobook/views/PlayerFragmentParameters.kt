package org.librarysimplified.audiobook.views

import androidx.annotation.ColorInt
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPlaybackRate.NORMAL_TIME
import java.io.Serializable

/**
 * Parameters for the main player fragment.
 */

data class PlayerFragmentParameters(

  /**
   * The primary color used to tint various views in the player.
   */

  @Deprecated(
    message = "Colors should now be taken from the colorPrimary attribute of the current application theme",
    level = DeprecationLevel.WARNING
  )
  @ColorInt val primaryColor: Int? = null,

  val currentRate: PlayerPlaybackRate? = NORMAL_TIME

) : Serializable
