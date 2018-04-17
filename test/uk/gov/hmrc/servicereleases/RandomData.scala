/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.servicedeployments

import java.time.{LocalDateTime, ZoneOffset}

import scala.util.Random

object RandomData {

  def string(length: Int) = {
    val r  = new scala.util.Random
    val sb = new StringBuilder
    for (i <- 1 to length) {
      sb.append(r.nextPrintableChar)
    }
    sb.toString
  }

  def date() = {
    val random        = new Random();
    val minDay        = LocalDateTime.of(1900, 1, 1, 12, 0).toEpochSecond(ZoneOffset.UTC)
    val maxDay        = LocalDateTime.of(2015, 1, 1, 12, 0).toEpochSecond(ZoneOffset.UTC)
    val randomSeconds = (minDay + random.nextDouble() * (maxDay - minDay)).toLong

    LocalDateTime.ofEpochSecond(randomSeconds, 0, ZoneOffset.UTC)
  }

}
