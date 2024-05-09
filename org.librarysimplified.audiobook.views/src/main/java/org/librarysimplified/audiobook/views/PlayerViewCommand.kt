package org.librarysimplified.audiobook.views

/**
 * The type of commands from the player views to the application.
 */

sealed class PlayerViewCommand {

  /**
   * The user performed an action that means the entire player should be closed.
   */

  data object PlayerViewNavigationCloseAll : PlayerViewCommand()

  /**
   * The user performed an action that means the TOC should be closed.
   */

  data object PlayerViewNavigationTOCClose : PlayerViewCommand()

  /**
   * The user performed an action that means the TOC should be opened.
   */

  data object PlayerViewNavigationTOCOpen : PlayerViewCommand()

  /**
   * The user performed an action that means the sleep timer menu should be opened.
   */

  data object PlayerViewNavigationSleepMenuOpen : PlayerViewCommand()

  /**
   * The user performed an action that means the playback rate menu should be opened.
   */

  data object PlayerViewNavigationPlaybackRateMenuOpen : PlayerViewCommand()

  /**
   * The player's cover image changed.
   */

  data object PlayerViewCoverImageChanged : PlayerViewCommand()
}
