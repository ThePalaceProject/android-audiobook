package org.librarysimplified.audiobook.views

import androidx.annotation.StringRes

interface PlayerBottomSheetType {

  fun drawerSetHandleAccessibilityStrings(
    @StringRes openHandle: Int,
    @StringRes closeHandle: Int
  )

  fun drawerOpen()

  fun drawerOpenInstantly()

  fun drawerClose()

  fun drawerCloseInstantly()

  fun toggleInstantly() {
    if (this.isOpen()) {
      this.drawerCloseInstantly()
    } else {
      this.drawerOpenInstantly()
    }
  }

  fun toggle() {
    if (this.isOpen()) {
      this.drawerClose()
    } else {
      this.drawerOpen()
    }
  }

  fun isOpen(): Boolean

  fun setOpenListener(listener: SheetOpenListenerType?)

  interface SheetOpenListenerType {
    fun onOpenChanged(state: Double)
  }
}
