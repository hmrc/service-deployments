package uk.gov.hmrc.servicereleases

import java.time.{LocalDateTime, ZoneOffset}

import scala.util.Random

object RandomData {

  def string(length: Int) = {
    val r = new scala.util.Random
    val sb = new StringBuilder
    for (i <- 1 to length) {
      sb.append(r.nextPrintableChar)
    }
    sb.toString
  }

  def date() = {
    val random = new Random();
    val minDay = LocalDateTime.of(1900, 1, 1, 12, 0).toEpochSecond(ZoneOffset.UTC)
    val maxDay = LocalDateTime.of(2015, 1, 1, 12, 0).toEpochSecond(ZoneOffset.UTC)
    val randomSeconds = (minDay + random.nextDouble() * (maxDay - minDay)).toLong

    LocalDateTime.ofEpochSecond(randomSeconds, 0, ZoneOffset.UTC)
  }

}
