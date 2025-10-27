package org.librarysimplified.audiobook.api

import java.util.concurrent.atomic.AtomicReference

/**
 * A value that indicates that an operation was performed by a particular thread.
 */

data class PlayerBlame(
  val where: Throwable,
  val threadName: String,
  val threadID: Long
) {

  companion object {

    fun closeIfOpen(
      ref: AtomicReference<PlayerBlame>,
      blame: () -> PlayerBlame
    ): Boolean {
      return ref.compareAndSet(null, blame())
    }

    fun closeIfOpen(
      ref: AtomicReference<PlayerBlame>
    ): Boolean {
      return closeIfOpen(ref, this::blame)
    }

    fun checkNotClosed(
      ref: AtomicReference<PlayerBlame>
    ) {
      val blame = ref.get()
      if (blame != null) {
        val exception =
          IllegalStateException(
            "Object has been closed by thread ${blame.threadID} (${blame.threadName})"
          )
        exception.initCause(blame.where)
        throw exception
      }
    }

    fun blame(): PlayerBlame {
      val exception = Exception()
      exception.fillInStackTrace()
      return PlayerBlame(
        where = exception,
        threadName = Thread.currentThread().name,
        threadID = Thread.currentThread().id
      )
    }
  }
}
