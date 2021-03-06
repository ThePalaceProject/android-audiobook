package org.librarysimplified.audiobook.tests

import org.joda.time.Duration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.views.PlayerTimeStrings

abstract class PlayerTimeStringsContract {

  private val spokenEnglish =
    PlayerTimeStrings.SpokenTranslations(
      hoursText = "hours", hourText = "hour",
      minutesText = "minutes", minuteText = "minute",
      secondsText = "seconds", secondText = "second"
    )

  private val spokenSpanish =
    PlayerTimeStrings.SpokenTranslations(
      hoursText = "horas", hourText = "hora",
      minutesText = "minutos", minuteText = "minuto",
      secondsText = "segundos", secondText = "segundo"
    )

  @Test
  fun testHourMinuteSecondsTextFromMillis_0() {
    Assertions.assertEquals(
      "00:00:00",
      PlayerTimeStrings.hourMinuteSecondTextFromMilliseconds(0)
    )
  }

  @Test
  fun testHourMinuteSecondsTextFromMillis_1() {
    Assertions.assertEquals(
      "00:00:01",
      PlayerTimeStrings.hourMinuteSecondTextFromMilliseconds(Duration.standardSeconds(1).millis)
    )
  }

  @Test
  fun testHourMinuteSecondsTextFromMillis_2() {
    Assertions.assertEquals(
      "00:01:00",
      PlayerTimeStrings.hourMinuteSecondTextFromMilliseconds(Duration.standardMinutes(1).millis)
    )
  }

  @Test
  fun testHourMinuteSecondsTextFromMillis_3() {
    Assertions.assertEquals(
      "01:00:00",
      PlayerTimeStrings.hourMinuteSecondTextFromMilliseconds(Duration.standardHours(1).millis)
    )
  }

  @Test
  fun testHourMinuteSecondsTextFromMillis_4() {
    Assertions.assertEquals(
      "59:59:59",
      PlayerTimeStrings.hourMinuteSecondTextFromMilliseconds(
        Duration.standardHours(59)
          .plus(Duration.standardMinutes(59))
          .plus(Duration.standardSeconds(59))
          .millis
      )
    )
  }

  @Test
  fun testHourMinuteSecondsTextFromDuration_0() {
    Assertions.assertEquals(
      "00:00:00",
      PlayerTimeStrings.hourMinuteSecondTextFromDuration(Duration.ZERO)
    )
  }

  @Test
  fun testHourMinuteSecondsTextFromDuration_1() {
    Assertions.assertEquals(
      "00:00:01",
      PlayerTimeStrings.hourMinuteSecondTextFromDuration(Duration.standardSeconds(1))
    )
  }

  @Test
  fun testHourMinuteSecondsTextFromDuration_2() {
    Assertions.assertEquals(
      "00:01:00",
      PlayerTimeStrings.hourMinuteSecondTextFromDuration(Duration.standardMinutes(1))
    )
  }

  @Test
  fun testHourMinuteSecondsTextFromDuration_3() {
    Assertions.assertEquals(
      "01:00:00",
      PlayerTimeStrings.hourMinuteSecondTextFromDuration(Duration.standardHours(1))
    )
  }

  @Test
  fun testHourMinuteSecondsTextFromDuration_4() {
    val time =
      Duration.standardHours(59)
        .plus(Duration.standardMinutes(59))
        .plus(Duration.standardSeconds(59))

    Assertions.assertEquals(
      "59:59:59",
      PlayerTimeStrings.hourMinuteSecondTextFromDuration(time)
    )
  }

  @Test
  fun testMinuteSecondsTextFromDuration_0() {
    Assertions.assertEquals(
      "00:00",
      PlayerTimeStrings.minuteSecondTextFromDuration(Duration.ZERO)
    )
  }

  @Test
  fun testMinuteSecondsTextFromDuration_1() {
    Assertions.assertEquals(
      "00:01",
      PlayerTimeStrings.minuteSecondTextFromDuration(Duration.standardSeconds(1))
    )
  }

  @Test
  fun testMinuteSecondsTextFromDuration_2() {
    Assertions.assertEquals(
      "01:00",
      PlayerTimeStrings.minuteSecondTextFromDuration(Duration.standardMinutes(1))
    )
  }

  @Test
  fun testMinuteSecondsTextFromDuration_4() {
    val time =
      Duration.standardMinutes(59)
        .plus(Duration.standardSeconds(59))

    Assertions.assertEquals(
      "59:59",
      PlayerTimeStrings.minuteSecondTextFromDuration(time)
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromMillis_0() {
    Assertions.assertEquals(
      "",
      PlayerTimeStrings.hourMinuteSecondSpokenFromMilliseconds(this.spokenEnglish, 0)
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromMillis_1() {
    Assertions.assertEquals(
      "1 second",
      PlayerTimeStrings.hourMinuteSecondSpokenFromMilliseconds(
        this.spokenEnglish,
        Duration.standardSeconds(1).millis
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromMillis_2() {
    Assertions.assertEquals(
      "1 minute",
      PlayerTimeStrings.hourMinuteSecondSpokenFromMilliseconds(
        this.spokenEnglish,
        Duration.standardMinutes(1).millis
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromMillis_3() {
    Assertions.assertEquals(
      "1 hour",
      PlayerTimeStrings.hourMinuteSecondSpokenFromMilliseconds(
        this.spokenEnglish,
        Duration.standardHours(1).millis
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromMillis_4() {
    Assertions.assertEquals(
      "59 hours 59 minutes 59 seconds",
      PlayerTimeStrings.hourMinuteSecondSpokenFromMilliseconds(
        this.spokenEnglish,
        Duration.standardHours(59)
          .plus(Duration.standardMinutes(59))
          .plus(Duration.standardSeconds(59))
          .millis
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromDuration_0() {
    Assertions.assertEquals(
      "",
      PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(this.spokenEnglish, Duration.ZERO)
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromDuration_1() {
    Assertions.assertEquals(
      "1 second",
      PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(
        this.spokenEnglish,
        Duration.standardSeconds(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromDuration_2() {
    Assertions.assertEquals(
      "1 minute",
      PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(
        this.spokenEnglish,
        Duration.standardMinutes(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromDuration_3() {
    Assertions.assertEquals(
      "1 hour",
      PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(
        this.spokenEnglish,
        Duration.standardHours(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromDuration_4() {
    val time =
      Duration.standardHours(59)
        .plus(Duration.standardMinutes(59))
        .plus(Duration.standardSeconds(59))

    Assertions.assertEquals(
      "59 hours 59 minutes 59 seconds",
      PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(this.spokenEnglish, time)
    )
  }

  @Test
  fun testMinuteSecondsSpokenEnglishFromDuration_0() {
    Assertions.assertEquals(
      "",
      PlayerTimeStrings.minuteSecondSpokenFromDuration(this.spokenEnglish, Duration.ZERO)
    )
  }

  @Test
  fun testMinuteSecondsSpokenEnglishFromDuration_1() {
    Assertions.assertEquals(
      "1 second",
      PlayerTimeStrings.minuteSecondSpokenFromDuration(
        this.spokenEnglish,
        Duration.standardSeconds(1)
      )
    )
  }

  @Test
  fun testMinuteSecondsSpokenEnglishFromDuration_2() {
    Assertions.assertEquals(
      "1 minute",
      PlayerTimeStrings.minuteSecondSpokenFromDuration(
        this.spokenEnglish,
        Duration.standardMinutes(1)
      )
    )
  }

  @Test
  fun testMinuteSecondsSpokenEnglishFromDuration_4() {
    val time =
      Duration.standardMinutes(59)
        .plus(Duration.standardSeconds(59))

    Assertions.assertEquals(
      "59 minutes 59 seconds",
      PlayerTimeStrings.minuteSecondSpokenFromDuration(this.spokenEnglish, time)
    )
  }

  @Test
  fun testMinuteSecondsSpokenEnglishFromDuration_5() {
    val time = Duration.standardMinutes(60)

    Assertions.assertEquals(
      "60 minutes",
      PlayerTimeStrings.minuteSecondSpokenFromDuration(this.spokenEnglish, time)
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromMillis_0() {
    Assertions.assertEquals(
      "",
      PlayerTimeStrings.hourMinuteSecondSpokenFromMilliseconds(this.spokenSpanish, 0)
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromMillis_1() {
    Assertions.assertEquals(
      "1 segundo",
      PlayerTimeStrings.hourMinuteSecondSpokenFromMilliseconds(
        this.spokenSpanish,
        Duration.standardSeconds(1).millis
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromMillis_2() {
    Assertions.assertEquals(
      "1 minuto",
      PlayerTimeStrings.hourMinuteSecondSpokenFromMilliseconds(
        this.spokenSpanish,
        Duration.standardMinutes(1).millis
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromMillis_3() {
    Assertions.assertEquals(
      "1 hora",
      PlayerTimeStrings.hourMinuteSecondSpokenFromMilliseconds(
        this.spokenSpanish,
        Duration.standardHours(1).millis
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromMillis_4() {
    Assertions.assertEquals(
      "59 horas 59 minutos 59 segundos",
      PlayerTimeStrings.hourMinuteSecondSpokenFromMilliseconds(
        this.spokenSpanish,
        Duration.standardHours(59)
          .plus(Duration.standardMinutes(59))
          .plus(Duration.standardSeconds(59))
          .millis
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromDuration_0() {
    Assertions.assertEquals(
      "",
      PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(this.spokenSpanish, Duration.ZERO)
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromDuration_1() {
    Assertions.assertEquals(
      "1 segundo",
      PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(
        this.spokenSpanish,
        Duration.standardSeconds(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromDuration_2() {
    Assertions.assertEquals(
      "1 minuto",
      PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(
        this.spokenSpanish,
        Duration.standardMinutes(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromDuration_3() {
    Assertions.assertEquals(
      "1 hora",
      PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(
        this.spokenSpanish,
        Duration.standardHours(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromDuration_4() {
    val time =
      Duration.standardHours(59)
        .plus(Duration.standardMinutes(59))
        .plus(Duration.standardSeconds(59))

    Assertions.assertEquals(
      "59 horas 59 minutos 59 segundos",
      PlayerTimeStrings.hourMinuteSecondSpokenFromDuration(this.spokenSpanish, time)
    )
  }

  @Test
  fun testMinuteSecondsSpokenSpanishFromDuration_0() {
    Assertions.assertEquals(
      "",
      PlayerTimeStrings.minuteSecondSpokenFromDuration(this.spokenSpanish, Duration.ZERO)
    )
  }

  @Test
  fun testMinuteSecondsSpokenSpanishFromDuration_1() {
    Assertions.assertEquals(
      "1 segundo",
      PlayerTimeStrings.minuteSecondSpokenFromDuration(
        this.spokenSpanish,
        Duration.standardSeconds(1)
      )
    )
  }

  @Test
  fun testMinuteSecondsSpokenSpanishFromDuration_2() {
    Assertions.assertEquals(
      "1 minuto",
      PlayerTimeStrings.minuteSecondSpokenFromDuration(
        this.spokenSpanish,
        Duration.standardMinutes(1)
      )
    )
  }

  @Test
  fun testMinuteSecondsSpokenSpanishFromDuration_4() {
    val time =
      Duration.standardMinutes(59)
        .plus(Duration.standardSeconds(59))

    Assertions.assertEquals(
      "59 minutos 59 segundos",
      PlayerTimeStrings.minuteSecondSpokenFromDuration(this.spokenSpanish, time)
    )
  }

  @Test
  fun testMinuteSecondsSpokenSpanishFromDuration_5() {
    val time = Duration.standardMinutes(60)

    Assertions.assertEquals(
      "60 minutos",
      PlayerTimeStrings.minuteSecondSpokenFromDuration(this.spokenSpanish, time)
    )
  }
}
