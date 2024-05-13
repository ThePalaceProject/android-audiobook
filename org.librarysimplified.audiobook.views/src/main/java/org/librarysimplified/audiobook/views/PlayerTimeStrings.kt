package org.librarysimplified.audiobook.views

import android.content.Context
import android.content.res.Resources
import org.joda.time.Duration
import org.joda.time.Period
import org.joda.time.format.PeriodFormatter
import org.joda.time.format.PeriodFormatterBuilder

object PlayerTimeStrings {

  /**
   * Spoken translations for words.
   */

  data class SpokenTranslations(

    /**
     * The word for "hours" in the current language.
     */

    val hoursText: String,

    /**
     * The word for "hour" in the current language.
     */

    val hourText: String,

    /**
     * The word for "minutes" in the current language.
     */

    val minutesText: String,

    /**
     * The word for "minute" in the current language.
     */

    val minuteText: String,

    /**
     * The word for "seconds" in the current language.
     */

    val secondsText: String,

    /**
     * The word for "second" in the current language.
     */

    val secondText: String,

    /**
     * The phrase for "remaining in chapter" in the current language, as in
     * "2 minutes remaining in chapter"
     */

    val remainingInChapter: String,

    /**
     * The phrase for "elapsed in chapter" in the current language, as in
     * "2 minutes elapsed in chapter"
     */

    val elapsedInChapter: String
  ) {

    fun minutes(minutes: Long): String =
      if (minutes > 1) this.minutesText else this.minuteText

    fun hours(hours: Long): String =
      if (hours > 1) this.hoursText else this.hourText

    fun seconds(seconds: Long): String =
      if (seconds > 1) this.secondsText else this.secondText

    companion object {

      fun createFromResources(resources: Resources): SpokenTranslations {
        return SpokenTranslations(
          hoursText = resources.getString(R.string.audiobook_accessibility_hours),
          hourText = resources.getString(R.string.audiobook_accessibility_hour),
          minutesText = resources.getString(R.string.audiobook_accessibility_minutes),
          minuteText = resources.getString(R.string.audiobook_accessibility_minute),
          secondsText = resources.getString(R.string.audiobook_accessibility_seconds),
          secondText = resources.getString(R.string.audiobook_accessibility_second),
          remainingInChapter = resources.getString(R.string.audiobook_accessibility_remaining_in_chapter),
          elapsedInChapter = resources.getString(R.string.audiobook_accessibility_elapsed_in_chapter),
        )
      }
    }
  }

  private val hourMinuteSecondFormatter: PeriodFormatter =
    PeriodFormatterBuilder()
      .printZeroAlways()
      .minimumPrintedDigits(2)
      .appendHours()
      .appendLiteral(":")
      .appendMinutes()
      .appendLiteral(":")
      .appendSeconds()
      .toFormatter()

  fun hourMinuteSecondTextFromMilliseconds(milliseconds: Long): String {
    return this.hourMinuteSecondFormatter.print(Duration.millis(milliseconds).toPeriod())
  }

  fun remainingBookTime(
    context: Context,
    remainingTime: Duration
  ): String {
    val hours =
      remainingTime.standardHours
    val withoutHours =
      remainingTime.minus(Duration.standardHours(hours))
    val minutes =
      withoutHours.standardMinutes

    if (hours > 0L) {
      return context.getString(R.string.audiobook_player_remaining_time, hours, minutes)
    }

    if (minutes > 0L) {
      return context.getString(R.string.audiobook_player_remaining_time_minutes_only, minutes)
    }

    return context.getString(
      R.string.audiobook_player_remaining_time_seconds_only,
      withoutHours.standardSeconds
    )
  }

  fun durationText(duration: Duration): String {
    return this.hourMinuteSecondFormatter.print(duration.toPeriod())
  }

  fun remainingTOCItemTime(
    time: Duration
  ): String {
    return this.hourMinuteSecondFormatter.print(time.toPeriod())
  }

  fun remainingTOCItemTimeSpoken(
    translations: SpokenTranslations,
    time: Duration
  ): String {
    val builder = this.durationSpokenBase(translations, time)
    builder.append(' ')
    builder.append(translations.remainingInChapter)
    return builder.toString().trim()
  }

  fun elapsedTOCItemTime(
    time: Duration
  ): CharSequence {
    return this.hourMinuteSecondFormatter.print(time.toPeriod())
  }

  fun elapsedTOCItemTimeSpoken(
    translations: SpokenTranslations,
    time: Duration
  ): CharSequence {
    val builder = this.durationSpokenBase(translations, time)

    builder.append(' ')
    builder.append(translations.elapsedInChapter)
    return builder.toString().trim()
  }

  private fun durationSpokenBase(
    translations: SpokenTranslations,
    time: Duration
  ): StringBuilder {
    val builder = StringBuilder(64)
    var period = time.toPeriod()

    val hours = period.toStandardHours()
    if (hours.hours > 0) {
      builder.append(hours.hours)
      builder.append(' ')
      builder.append(translations.hours(hours.hours.toLong()))
      builder.append(' ')
      period = period.minus(hours)
    }

    val minutes = period.toStandardMinutes()
    if (minutes.minutes > 0) {
      builder.append(minutes.minutes)
      builder.append(' ')
      builder.append(translations.minutes(minutes.minutes.toLong()))
      builder.append(' ')
      period = period.minus(Period.minutes(minutes.minutes))
    }

    val seconds = period.toStandardSeconds()
    if (seconds.seconds > 0) {
      builder.append(seconds.seconds)
      builder.append(' ')
      builder.append(translations.seconds(seconds.seconds.toLong()))
    }
    return builder
  }

  fun durationSpoken(
    timeStrings: SpokenTranslations,
    duration: Duration
  ): String {
    return this.durationSpokenBase(timeStrings, duration).toString().trim()
  }
}
