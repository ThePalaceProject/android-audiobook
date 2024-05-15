package org.librarysimplified.audiobook.manifest.api

import com.io7m.kabstand.core.IntervalType

/**
 * Millisecond values on different timelines. These types exist because it is very easy to
 * accidentally mix up, for example, "millisecond values relative to a TOC item" and
 * "millisecond values relative to a reading order item". Using one type of millisecond time
 * value where a different type of time value was expected usually results in bizarre and
 * hard-to-explain issues. Making all of the time values type-distinct ensures that the compiler
 * prevents us from mixing them up.
 */

/**
 * A millisecond value on the absolute timeline.
 */

data class PlayerMillisecondsAbsolute(
  val value: Long
) : Comparable<PlayerMillisecondsAbsolute> {
  init {
    check(this.value >= 0) {
      "Absolute times cannot be negative."
    }
  }

  operator fun plus(x: PlayerMillisecondsReadingOrderItem): PlayerMillisecondsAbsolute {
    return PlayerMillisecondsAbsolute(this.value.plus(x.value))
  }

  operator fun plus(x: PlayerMillisecondsAbsolute): PlayerMillisecondsAbsolute {
    return PlayerMillisecondsAbsolute(this.value.plus(x.value))
  }

  operator fun minus(x: PlayerMillisecondsAbsolute): PlayerMillisecondsAbsolute {
    return PlayerMillisecondsAbsolute(this.value.minus(x.value))
  }

  override fun compareTo(other: PlayerMillisecondsAbsolute): Int {
    return this.value.compareTo(other.value)
  }

  override fun toString(): String {
    return this.value.toString()
  }
}

data class PlayerMillisecondsAbsoluteInterval(
  val lower: PlayerMillisecondsAbsolute,
  val upper: PlayerMillisecondsAbsolute
) : IntervalType<PlayerMillisecondsAbsolute> {
  override fun lower(): PlayerMillisecondsAbsolute {
    return this.lower
  }

  override fun size(): PlayerMillisecondsAbsolute {
    return PlayerMillisecondsAbsolute(1L + (this.upper.value - this.lower.value))
  }

  override fun upper(): PlayerMillisecondsAbsolute {
    return this.upper
  }

  override fun upperMaximum(
    other: IntervalType<PlayerMillisecondsAbsolute>
  ): IntervalType<PlayerMillisecondsAbsolute> {
    return PlayerMillisecondsAbsoluteInterval(
      this.lower,
      PlayerMillisecondsAbsolute(Math.max(this.upper.value, other.upper().value))
    )
  }

  override fun overlaps(
    other: IntervalType<PlayerMillisecondsAbsolute>
  ): Boolean {
    return (this.lower <= other.upper() && other.lower() <= this.upper)
  }
}

/**
 * A millisecond value relative to a TOC item.
 */

data class PlayerMillisecondsTOC(
  val value: Long
) : Comparable<PlayerMillisecondsTOC> {
  override fun compareTo(other: PlayerMillisecondsTOC): Int {
    return this.value.compareTo(other.value)
  }

  operator fun plus(x: PlayerMillisecondsTOC): PlayerMillisecondsTOC {
    return PlayerMillisecondsTOC(this.value.plus(x.value))
  }

  operator fun minus(x: PlayerMillisecondsTOC): PlayerMillisecondsTOC {
    return PlayerMillisecondsTOC(this.value.minus(x.value))
  }

  override fun toString(): String {
    return this.value.toString()
  }
}

/**
 * A millisecond value relative to a reading order item.
 */

data class PlayerMillisecondsReadingOrderItem(
  val value: Long
) : Comparable<PlayerMillisecondsReadingOrderItem> {
  override fun compareTo(other: PlayerMillisecondsReadingOrderItem): Int {
    return this.value.compareTo(other.value)
  }

  operator fun plus(x: PlayerMillisecondsReadingOrderItem): PlayerMillisecondsReadingOrderItem {
    return PlayerMillisecondsReadingOrderItem(this.value.plus(x.value))
  }

  operator fun minus(x: PlayerMillisecondsReadingOrderItem): PlayerMillisecondsReadingOrderItem {
    return PlayerMillisecondsReadingOrderItem(this.value.minus(x.value))
  }

  override fun toString(): String {
    return this.value.toString()
  }
}
