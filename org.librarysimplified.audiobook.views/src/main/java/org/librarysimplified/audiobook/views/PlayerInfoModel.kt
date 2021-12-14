package org.librarysimplified.audiobook.views

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import org.librarysimplified.audiobook.api.PlayerType

data class PlayerInfoModel(
  val bookChapterName: String,
  val bookCover: Bitmap?,
  val bookName: String,
  val isPlaying: Boolean,
  val player: PlayerType,
  @DrawableRes
  val smallIcon: Int
)
