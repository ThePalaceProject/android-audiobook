package org.librarysimplified.audiobook.views

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

/**
 * Functions to resolve colors from themes.
 *
 * @since 2.0.0
 */

internal object PlayerColors {

  /**
   * The hint color for components.
   */

  @JvmStatic
  @ColorInt
  fun hintColor(
    context: Context
  ): Int {
    return ContextCompat.getColor(context, android.R.color.darker_gray)
  }
}
