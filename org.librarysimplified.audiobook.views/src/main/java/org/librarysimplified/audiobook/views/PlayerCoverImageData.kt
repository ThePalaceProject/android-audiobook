package org.librarysimplified.audiobook.views

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * A cover image, and the compressed JPEG bytes of the image.
 */

data class PlayerCoverImageData(
  val cover: Bitmap,
  val data: ByteArray
) {
  companion object {
    fun create(cover: Bitmap): PlayerCoverImageData {
      val data =
        ByteArrayOutputStream()
          .use { stream ->
            cover.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            stream.toByteArray()
          }
      return PlayerCoverImageData(cover, data)
    }
  }
}
