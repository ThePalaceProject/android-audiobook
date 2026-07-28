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

data class PlayerMillisecondsAbsolute(
  val value: Long
) : Comparable<PlayerMillisecondsAbsolute> {
  init {
    check(this.value >= 0) {
      "Absolute times cannot be negative."
    }
  }

  operator fun plus(x: PlayerMillisecondsReadingOrderItem): PlayerMillisecondsAbsolute =
    PlayerMillisecondsAbsolute(this.value.plus(x.value))

  operator fun plus(x: PlayerMillisecondsAbsolute): PlayerMillisecondsAbsolute = PlayerMillisecondsAbsolute(this.value.plus(x.value))

  operator fun minus(x: PlayerMillisecondsAbsolute): PlayerMillisecondsAbsolute = PlayerMillisecondsAbsolute(this.value.minus(x.value))

  override fun compareTo(other: PlayerMillisecondsAbsolute): Int = this.value.compareTo(other.value)

  override fun toString(): String = this.value.toString()
}

data class PlayerMillisecondsAbsoluteInterval(
  val lower: PlayerMillisecondsAbsolute,
  val upper: PlayerMillisecondsAbsolute
) : IntervalType<PlayerMillisecondsAbsolute> {
  override fun lower(): PlayerMillisecondsAbsolute = this.lower

  override fun size(): PlayerMillisecondsAbsolute = PlayerMillisecondsAbsolute(1L + (this.upper.value - this.lower.value))

  override fun upper(): PlayerMillisecondsAbsolute = this.upper

  override fun upperMaximum(other: IntervalType<PlayerMillisecondsAbsolute>): IntervalType<PlayerMillisecondsAbsolute> =
    PlayerMillisecondsAbsoluteInterval(
      this.lower,
      PlayerMillisecondsAbsolute(Math.max(this.upper.value, other.upper().value))
    )

  override fun overlaps(other: IntervalType<PlayerMillisecondsAbsolute>): Boolean =
    (this.lower <= other.upper() && other.lower() <= this.upper)
}

/**
 * A millisecond value relative to a TOC item.
 */

data class PlayerMillisecondsTOC(
  val value: Long
) : Comparable<PlayerMillisecondsTOC> {
  override fun compareTo(other: PlayerMillisecondsTOC): Int = this.value.compareTo(other.value)

  operator fun plus(x: PlayerMillisecondsTOC): PlayerMillisecondsTOC = PlayerMillisecondsTOC(this.value.plus(x.value))

  operator fun minus(x: PlayerMillisecondsTOC): PlayerMillisecondsTOC = PlayerMillisecondsTOC(this.value.minus(x.value))

  override fun toString(): String = this.value.toString()
}

/**
 * A millisecond value relative to a reading order item.
 */

data class PlayerMillisecondsReadingOrderItem(
  val value: Long
) : Comparable<PlayerMillisecondsReadingOrderItem> {
  override fun compareTo(other: PlayerMillisecondsReadingOrderItem): Int = this.value.compareTo(other.value)

  operator fun plus(x: PlayerMillisecondsReadingOrderItem): PlayerMillisecondsReadingOrderItem =
    PlayerMillisecondsReadingOrderItem(
      this.value
        .plus(x.value)
    )

  operator fun minus(x: PlayerMillisecondsReadingOrderItem): PlayerMillisecondsReadingOrderItem =
    PlayerMillisecondsReadingOrderItem(
      this.value
        .minus(x.value)
    )

  override fun toString(): String = this.value.toString()
}
