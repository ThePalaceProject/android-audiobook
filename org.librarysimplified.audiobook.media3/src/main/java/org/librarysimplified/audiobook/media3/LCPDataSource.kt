package org.librarysimplified.audiobook.media3

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.runBlocking
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.ErrorException
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.resource.BufferingResource
import org.readium.r2.shared.util.resource.Resource
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * An ExoPlayer DataSource that retrieves resources from a readium Publication, decrypting them if
 * necessary.
 *
 * This file is based on PublicationDataSource.kt from kotlin-toolkit:
 * https://github.com/readium/kotlin-toolkit/blob/main/readium/navigator/src/main/java/org/readium/r2/navigator/audio/PublicationDataSource.kt
 *
 * It has been modified to be compatible with Media3.
 */

internal class LCPDataSource(
  private val publication: Publication
) : DataSource {

  private val logger =
    LoggerFactory.getLogger(LCPDataSource::class.java)

  class Factory(private val publication: Publication) : DataSource.Factory {
    override fun createDataSource(): LCPDataSource {
      return LCPDataSource(this.publication)
    }
  }

  sealed class Exception(message: String, cause: Throwable?) : IOException(message, cause) {
    class NotOpened(message: String) : Exception(message, null)
    class NotFound(message: String) : Exception(message, null)
    class ReadFailed(uri: Uri, offset: Int, readLength: Int, cause: Throwable) :
      Exception("Failed to read $readLength bytes of URI $uri at offset $offset.", cause)
  }

  private data class OpenedResource(
    val resource: Resource,
    val uri: Uri,
    var position: Long,
  )

  private var openedResource: OpenedResource? = null

  override fun addTransferListener(transferListener: TransferListener) {
    // do nothing
  }

  override fun open(dataSpec: DataSpec): Long {
    val url =
      org.readium.r2.shared.util.Url(dataSpec.uri.toString())!!
    val link =
      this.publication.linkWithHref(url)
        ?: throw Exception.NotFound("Resource not found in manifest: ${dataSpec.uri}")

    val resource =
      this.publication.get(link)
        ?: throw Exception.NotFound("Resource not found in manifest: ${dataSpec.uri}")

    val buffered =
      BufferingResource(
        resource = resource,
        resourceLength = this.cachedLengths[dataSpec.uri.toString()]
      )

    this.openedResource = OpenedResource(
      resource = buffered,
      uri = dataSpec.uri,
      position = dataSpec.position,
    )

    val bytesToRead =
      if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
        dataSpec.length
      } else {
        val contentLength = this.contentLengthOf(dataSpec.uri, resource)
          ?: return dataSpec.length
        contentLength - dataSpec.position
      }

    return bytesToRead
  }

  private var cachedLengths: MutableMap<String, Long> = mutableMapOf()

  private fun contentLengthOf(uri: Uri, resource: Resource): Long? {
    this.cachedLengths[uri.toString()]?.let { return it }

    val length = runBlocking { resource.length() }.getOrNull()
      ?: return null

    this.cachedLengths[uri.toString()] = length
    return length
  }

  override fun read(target: ByteArray, offset: Int, length: Int): Int {
    if (length <= 0) {
      return 0
    }

    val openedResource = this.openedResource
      ?: throw Exception.NotOpened("No opened resource to read from. Did you call open()?")

    try {
      val data = runBlocking {
        when (val r =
          openedResource.resource
            .read(range = openedResource.position until (openedResource.position + length))
        ) {
          is Try.Failure -> throw ErrorException(r.value)
          is Try.Success -> r.value
        }
      }

      if (data.isEmpty()) {
        return C.RESULT_END_OF_INPUT
      }

      data.copyInto(
        destination = target,
        destinationOffset = offset,
        startIndex = 0,
        endIndex = data.size
      )

      openedResource.position += data.count()
      return data.count()
    } catch (e: Exception) {
      throw Exception.ReadFailed(
        uri = openedResource.uri,
        offset = offset,
        readLength = length,
        cause = e
      )
    }
  }

  override fun getUri(): Uri? {
    return this.openedResource?.uri
  }

  override fun close() {
    this.openedResource?.run {
      try {
        this@run.resource.close()
      } catch (e: Exception) {
        throw e
      }
    }
    this.openedResource = null
  }
}
