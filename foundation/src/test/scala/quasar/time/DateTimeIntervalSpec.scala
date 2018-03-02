/*
 * Copyright 2014–2018 SlamData Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quasar.time

import slamdata.Predef._

import java.time._

import org.specs2.matcher.{Expectable, MatchResult, Matcher}

class DateTimeIntervalSpec extends quasar.Qspec {

  "parsing" should {
    "not parse just a P" in {
      DateTimeInterval.parse("P") shouldEqual None
    }

    "not parse just a fraction" in {
      DateTimeInterval.parse("PT.005S") shouldEqual None
    }

    "parse just a year" in {
      DateTimeInterval.parse("P1Y") shouldEqual
        Some(DateTimeInterval.make(1, 0, 0, 0, 0))
    }

    "parse just a month" in {
      DateTimeInterval.parse("P1M") shouldEqual
        Some(DateTimeInterval.make(0, 1, 0, 0, 0))
    }

    "parse just a week" in {
      DateTimeInterval.parse("P1W") shouldEqual
        Some(DateTimeInterval.make(0, 0, 7, 0, 0))
    }

    "parse just a day" in {
      DateTimeInterval.parse("P1D") shouldEqual
        Some(DateTimeInterval.make(0, 0, 1, 0, 0))
    }

    "parse just an hour" in {
      DateTimeInterval.parse("PT1H") shouldEqual
        Some(DateTimeInterval.make(0, 0, 0, 60L * 60, 0))
    }

    "parse just a minute" in {
      DateTimeInterval.parse("PT1M") shouldEqual
        Some(DateTimeInterval.make(0, 0, 0, 60L, 0))
    }

    "parse just a second" in {
      DateTimeInterval.parse("PT1S") shouldEqual
        Some(DateTimeInterval.make(0, 0, 0, 1, 0))
    }

    "parse a composite duration with every component" in {
      DateTimeInterval.parse("P1Y2M3W4DT5H6M7.8S") shouldEqual
        Some(DateTimeInterval.make(1, 2, 25, 18367L, 800000000))
    }

    "parse a negated composite duration with every component" in {
      DateTimeInterval.parse("-P1Y2M3W4DT5H6M7.8S") shouldEqual
        Some(DateTimeInterval.make(-1, -2, -25, -18367L, -800000000))
    }

    "parse a composite duration with every component negated" in {
      DateTimeInterval.parse("P-1Y-2M-3W-4DT-5H-6M-7.8S") shouldEqual
        Some(DateTimeInterval.make(-1, -2, -25, -18367L, -800000000))
    }

    "parse should handle negative nanos with seconds == 0" in {
      val str = "P2131840384M91413566DT-488689H-42M-0.917127768S"
      DateTimeInterval.parse(str).map(_.toString) shouldEqual Some(str)
    }.pendingUntilFixed("https://bugs.openjdk.java.net/browse/JDK-8054978")
  }

  "toString" should {
    "empty" in {
      DateTimeInterval.make(0, 0, 0, 0L, 0).toString shouldEqual "P0D"
    }

    "one year" in {
      DateTimeInterval.make(1, 0, 0, 0L, 0).toString shouldEqual "P1Y"
    }

    "one month" in {
      DateTimeInterval.make(0, 1, 0, 0L, 0).toString shouldEqual "P1M"
    }

    "one day" in {
      DateTimeInterval.make(0, 0, 1, 0L, 0).toString shouldEqual "P1D"
    }

    "one hour" in {
      DateTimeInterval.make(0, 0, 0, 3600L, 0).toString shouldEqual "PT1H"
    }

    "one minute" in {
      DateTimeInterval.make(0, 0, 0, 60L, 0).toString shouldEqual "PT1M"
    }

    "one second" in {
      DateTimeInterval.make(0, 0, 0, 1L, 0).toString shouldEqual "PT1S"
    }

    "one tenth second" in {
      DateTimeInterval.make(0, 0, 0, 0L, 100000000).toString shouldEqual "PT0.1S"
    }

    "all components" in {
      DateTimeInterval.make(1, 1, 1, 3661L, 100000000).toString shouldEqual "P1Y1M1DT1H1M1.1S"
    }

    "negative nanos" in {
      Duration.ofSeconds(1, -100000000).toString shouldEqual "PT0.9S"
      DateTimeInterval.make(0, 0, 0, 1L, -100000000).toString shouldEqual "PT0.9S"
    }
  }

  "factories" should {
    "one year" in {
      DateTimeInterval.ofYears(1) shouldEqual DateTimeInterval.make(1, 0, 0, 0, 0)
    }

    "one month" in {
      DateTimeInterval.ofMonths(1) shouldEqual DateTimeInterval.make(0, 1, 0, 0, 0)
    }

    "one day" in {
      DateTimeInterval.ofDays(1) shouldEqual DateTimeInterval.make(0, 0, 1, 0, 0)
    }

    "one hour" in {
      DateTimeInterval.ofHours(1L) shouldEqual DateTimeInterval.make(0, 0, 0, 3600, 0)
    }

    "one minute" in {
      DateTimeInterval.ofMinutes(1L) shouldEqual DateTimeInterval.make(0, 0, 0, 60, 0)
    }

    "one second" in {
      DateTimeInterval.ofSeconds(1L) shouldEqual DateTimeInterval.make(0, 0, 0, 1, 0)
    }

    "one second and a half" in {
      DateTimeInterval.ofSecondsNanos(1L, 500000000L) shouldEqual DateTimeInterval.make(0, 0, 0, 1L, 500000000L)
    }

    "one half-second" in {
      DateTimeInterval.ofNanos(500000000L) shouldEqual DateTimeInterval.make(0, 0, 0, 0L, 500000000L)
    }

    "one millis" in {
      DateTimeInterval.ofMillis(1L) shouldEqual DateTimeInterval.make(0, 0, 0, 0L, 1000000L)
    }

    "one nano" in {
      DateTimeInterval.ofNanos(1L) shouldEqual DateTimeInterval.make(0, 0, 0, 0L, 1L)
    }

    "negative nanos" in {
      DateTimeInterval.ofNanos(-1L) shouldEqual DateTimeInterval.make(0, 0, 0, -1L, 999999999L)
    }
  }

  "between" >> {
    val d1: LocalDate = LocalDate.of(1999, 12, 3)
    val d2: LocalDate = LocalDate.of(2004, 7, 21)

    val t1: LocalTime = LocalTime.of(14, 38, 17, 123456789)
    val t2: LocalTime = LocalTime.of(2, 59, 8, 38291)

    val dt1: LocalDateTime = LocalDateTime.of(d1, t1)
    val dt2: LocalDateTime = LocalDateTime.of(d2, t2)

    val o1: ZoneOffset = ZoneOffset.ofHoursMinutesSeconds(4, 49, 18)
    val o2: ZoneOffset = ZoneOffset.ofHoursMinutes(-8, -18)

    val od1: OffsetDate = OffsetDate(d1, o1)
    val od2: OffsetDate = OffsetDate(d2, o2)

    val ot1: OffsetTime = OffsetTime.of(t1, o1)
    val ot2: OffsetTime = OffsetTime.of(t2, o2)

    val odt1: OffsetDateTime = OffsetDateTime.of(d1, t1, o1)
    val odt2: OffsetDateTime = OffsetDateTime.of(d2, t2, o2)

    "compute difference between two LocalDate" >> {
      val diff1 = DateTimeInterval.betweenLocalDate(d1, d2)
      val diff2 = DateTimeInterval.betweenLocalDate(d2, d1)

      diff1.addToLocalDate(d1) must_== d2
      diff1.subtractFromLocalDate(d2) must_== d1

      diff2.addToLocalDate(d2) must_== d1
      diff2.subtractFromLocalDate(d1) must_== d2
    }

    "compute difference between two LocalTime" >> {
      val diff1 = DateTimeInterval.betweenLocalTime(t1, t2)
      val diff2 = DateTimeInterval.betweenLocalTime(t2, t1)

      diff1.addToLocalTime(t1) must_== t2
      diff1.subtractFromLocalTime(t2) must_== t1

      diff2.addToLocalTime(t2) must_== t1
      diff2.subtractFromLocalTime(t1) must_== t2
    }

    "compute difference between two LocalDateTime" >> {
      val diff1 = DateTimeInterval.betweenLocalDateTime(dt1, dt2)
      val diff2 = DateTimeInterval.betweenLocalDateTime(dt2, dt1)

      diff1.addToLocalDateTime(dt1) must_== dt2
      diff1.subtractFromLocalDateTime(dt2) must_== dt1

      diff2.addToLocalDateTime(dt2) must_== dt1
      diff2.subtractFromLocalDateTime(dt1) must_== dt2
    }

    "compute difference between two OffsetDate" >> {
      val diff1 = DateTimeInterval.betweenOffsetDate(od1, od2)
      val diff2 = DateTimeInterval.betweenOffsetDate(od2, od1)

      diff1.addToOffsetDate(od1) must equalOffsetDate(od2)
      diff1.subtractFromOffsetDate(od2) must equalOffsetDate(od1)

      diff2.addToOffsetDate(od2) must equalOffsetDate(od1)
      diff2.subtractFromOffsetDate(od1) must equalOffsetDate(od2)
    }

    "compute difference between two OffsetTime" >> {
      val diff1 = DateTimeInterval.betweenOffsetTime(ot1, ot2)
      val diff2 = DateTimeInterval.betweenOffsetTime(ot2, ot1)

      diff1.addToOffsetTime(ot1) must equalOffsetTime(ot2)
      diff1.subtractFromOffsetTime(ot2) must equalOffsetTime(ot1)

      diff2.addToOffsetTime(ot2) must equalOffsetTime(ot1)
      diff2.subtractFromOffsetTime(ot1) must equalOffsetTime(ot2)
    }

    "compute difference between two OffsetDateTime" >> {
      val diff1 = DateTimeInterval.betweenOffsetDateTime(odt1, odt2)
      val diff2 = DateTimeInterval.betweenOffsetDateTime(odt2, odt1)

      diff1.addToOffsetDateTime(odt1) must equalOffsetDateTime(odt2)
      diff1.subtractFromOffsetDateTime(odt2) must equalOffsetDateTime(odt1)

      diff2.addToOffsetDateTime(odt2) must equalOffsetDateTime(odt1)
      diff2.subtractFromOffsetDateTime(odt1) must equalOffsetDateTime(odt2)
    }
  }

  def equalOffsetDate(expected: OffsetDate): Matcher[OffsetDate] = {
    new Matcher[OffsetDate] {
      def apply[S <: OffsetDate](s: Expectable[S]): MatchResult[S] = {
        val msg: String = s"Actual: ${s.value}\nExpected: $expected\n"

        if (expected.date == s.value.date)
          success(msg, s)
        else
          failure(msg, s)
      }
    }
  }

  def equalOffsetTime(expected: OffsetTime): Matcher[OffsetTime] = {
    new Matcher[OffsetTime] {
      def apply[S <: OffsetTime](s: Expectable[S]): MatchResult[S] = {
        val msg: String = s"Actual: ${s.value}\nExpected: $expected\n"

        if (expected.isEqual(s.value))
          success(msg, s)
        else
          failure(msg, s)
      }
    }
  }

  def equalOffsetDateTime(expected: OffsetDateTime): Matcher[OffsetDateTime] = {
    new Matcher[OffsetDateTime] {
      def apply[S <: OffsetDateTime](s: Expectable[S]): MatchResult[S] = {
        val msg: String = s"Actual: ${s.value}\nExpected: $expected\n"

        if (expected.isEqual(s.value))
          success(msg, s)
        else
          failure(msg, s)
      }
    }
  }
}
