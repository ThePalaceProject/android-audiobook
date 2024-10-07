package org.librarysimplified.audiobook.views.mediacontrols

/**
 * Commands sent to the player service from the model. Primarily this just exists in order to
 * get an orderly shutdown for the service; the model itself can't get or hold a reference to
 * the service, so otherwise has no way to send it commands.
 */

sealed class PlayerServiceCommand {

  /**
   * The player service should shut down now.
   */

  data object PlayerServiceShutDown : PlayerServiceCommand()
}
