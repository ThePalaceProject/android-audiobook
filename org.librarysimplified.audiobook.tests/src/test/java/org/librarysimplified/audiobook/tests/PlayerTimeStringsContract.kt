package org.librarysimplified.audiobook.tests

import org.joda.time.Duration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.views.PlayerTimeStrings

abstract class PlayerTimeStringsContract {

  private val spokenEnglish =
    PlayerTimeStrings.SpokenTranslations(
      hoursText = "hours",
      hourText = "hour",
      minutesText = "minutes",
      minuteText = "minute",
      secondsText = "seconds",
      secondText = "second",
      remainingInChapter = "remaining in chapter",
      elapsedInChapter = "elapsed in chapter"
    )

  private val spokenSpanish =
    PlayerTimeStrings.SpokenTranslations(
      hoursText = "horas",
      hourText = "hora",
      minutesText = "minutos",
      minuteText = "minuto",
      secondsText = "segundos",
      secondText = "segundo",
      remainingInChapter = "restante en el capítulo",
      elapsedInChapter = "transcurrido en el capitulo"
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
      PlayerTimeStrings.durationText(Duration.ZERO)
    )
  }

  @Test
  fun testHourMinuteSecondsTextFromDuration_1() {
    Assertions.assertEquals(
      "00:00:01",
      PlayerTimeStrings.durationText(Duration.standardSeconds(1))
    )
  }

  @Test
  fun testHourMinuteSecondsTextFromDuration_2() {
    Assertions.assertEquals(
      "00:01:00",
      PlayerTimeStrings.durationText(Duration.standardMinutes(1))
    )
  }

  @Test
  fun testHourMinuteSecondsTextFromDuration_3() {
    Assertions.assertEquals(
      "01:00:00",
      PlayerTimeStrings.durationText(Duration.standardHours(1))
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
      PlayerTimeStrings.durationText(time)
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromMillis_0() {
    Assertions.assertEquals(
      "",
      PlayerTimeStrings.durationSpoken(this.spokenEnglish, Duration.ZERO)
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromMillis_1() {
    Assertions.assertEquals(
      "1 second",
      PlayerTimeStrings.durationSpoken(
        this.spokenEnglish,
        Duration.standardSeconds(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromMillis_2() {
    Assertions.assertEquals(
      "1 minute",
      PlayerTimeStrings.durationSpoken(
        this.spokenEnglish,
        Duration.standardMinutes(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromMillis_3() {
    Assertions.assertEquals(
      "1 hour",
      PlayerTimeStrings.durationSpoken(
        this.spokenEnglish,
        Duration.standardHours(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromMillis_4() {
    Assertions.assertEquals(
      "59 hours 59 minutes 59 seconds",
      PlayerTimeStrings.durationSpoken(
        this.spokenEnglish,
        Duration.standardHours(59)
          .plus(Duration.standardMinutes(59))
          .plus(Duration.standardSeconds(59))
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromDuration_0() {
    Assertions.assertEquals(
      "",
      PlayerTimeStrings.durationSpoken(this.spokenEnglish, Duration.ZERO)
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromDuration_1() {
    Assertions.assertEquals(
      "1 second",
      PlayerTimeStrings.durationSpoken(
        this.spokenEnglish,
        Duration.standardSeconds(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromDuration_2() {
    Assertions.assertEquals(
      "1 minute",
      PlayerTimeStrings.durationSpoken(
        this.spokenEnglish,
        Duration.standardMinutes(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenEnglishFromDuration_3() {
    Assertions.assertEquals(
      "1 hour",
      PlayerTimeStrings.durationSpoken(
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
      PlayerTimeStrings.durationSpoken(this.spokenEnglish, time)
    )
  }

  @Test
  fun testMinuteSecondsSpokenEnglishFromDuration_0() {
    Assertions.assertEquals(
      "",
      PlayerTimeStrings.durationSpoken(this.spokenEnglish, Duration.ZERO)
    )
  }

  @Test
  fun testMinuteSecondsSpokenEnglishFromDuration_1() {
    Assertions.assertEquals(
      "1 second",
      PlayerTimeStrings.durationSpoken(
        this.spokenEnglish,
        Duration.standardSeconds(1)
      )
    )
  }

  @Test
  fun testMinuteSecondsSpokenEnglishFromDuration_2() {
    Assertions.assertEquals(
      "1 minute",
      PlayerTimeStrings.durationSpoken(
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
      PlayerTimeStrings.durationSpoken(this.spokenEnglish, time)
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromMillis_0() {
    Assertions.assertEquals(
      "",
      PlayerTimeStrings.durationSpoken(this.spokenSpanish, Duration.ZERO)
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromMillis_1() {
    Assertions.assertEquals(
      "1 segundo",
      PlayerTimeStrings.durationSpoken(
        this.spokenSpanish,
        Duration.standardSeconds(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromMillis_2() {
    Assertions.assertEquals(
      "1 minuto",
      PlayerTimeStrings.durationSpoken(
        this.spokenSpanish,
        Duration.standardMinutes(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromMillis_3() {
    Assertions.assertEquals(
      "1 hora",
      PlayerTimeStrings.durationSpoken(
        this.spokenSpanish,
        Duration.standardHours(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromMillis_4() {
    Assertions.assertEquals(
      "59 horas 59 minutos 59 segundos",
      PlayerTimeStrings.durationSpoken(
        this.spokenSpanish,
        Duration.standardHours(59)
          .plus(Duration.standardMinutes(59))
          .plus(Duration.standardSeconds(59))
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromDuration_0() {
    Assertions.assertEquals(
      "",
      PlayerTimeStrings.durationSpoken(this.spokenSpanish, Duration.ZERO)
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromDuration_1() {
    Assertions.assertEquals(
      "1 segundo",
      PlayerTimeStrings.durationSpoken(
        this.spokenSpanish,
        Duration.standardSeconds(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromDuration_2() {
    Assertions.assertEquals(
      "1 minuto",
      PlayerTimeStrings.durationSpoken(
        this.spokenSpanish,
        Duration.standardMinutes(1)
      )
    )
  }

  @Test
  fun testHourMinuteSecondsSpokenSpanishFromDuration_3() {
    Assertions.assertEquals(
      "1 hora",
      PlayerTimeStrings.durationSpoken(
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
      PlayerTimeStrings.durationSpoken(this.spokenSpanish, time)
    )
  }

  @Test
  fun testMinuteSecondsSpokenSpanishFromDuration_0() {
    Assertions.assertEquals(
      "",
      PlayerTimeStrings.durationSpoken(this.spokenSpanish, Duration.ZERO)
    )
  }

  @Test
  fun testMinuteSecondsSpokenSpanishFromDuration_1() {
    Assertions.assertEquals(
      "1 segundo",
      PlayerTimeStrings.durationSpoken(
        this.spokenSpanish,
        Duration.standardSeconds(1)
      )
    )
  }

  @Test
  fun testMinuteSecondsSpokenSpanishFromDuration_2() {
    Assertions.assertEquals(
      "1 minuto",
      PlayerTimeStrings.durationSpoken(
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
      PlayerTimeStrings.durationSpoken(this.spokenSpanish, time)
    )
  }
}
