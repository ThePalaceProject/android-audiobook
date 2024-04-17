package org.librarysimplified.audiobook.views

import android.content.Intent
import android.graphics.Bitmap
import androidx.annotation.DrawableRes

data class PlayerInfoModel(
  val bookChapterName: String,
  val bookCover: Bitmap?,
  val bookName: String,
  val isPlaying: Boolean,
  @DrawableRes
  val smallIcon: Int,
  val notificationIntent: Intent
)
