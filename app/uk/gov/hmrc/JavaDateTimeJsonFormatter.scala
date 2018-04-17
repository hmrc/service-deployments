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

package uk.gov.hmrc
import java.time._

import _root_.play.api.libs.json._

object JavaDateTimeJsonFormatter {

  implicit val localDateTimeReads = new Reads[LocalDateTime] {
    override def reads(json: JsValue): JsResult[LocalDateTime] = json match {
      case JsNumber(v) =>
        JsSuccess(
          LocalDateTime.ofEpochSecond(v.toLongExact, 0, ZoneOffset.UTC)
        )
      case v => JsError(s"invalid value for epoch second '$v'")
    }
  }

  implicit val localDateTimeWrites = new Writes[LocalDateTime] {
    override def writes(o: LocalDateTime): JsValue =
      JsNumber(o.toEpochSecond(ZoneOffset.UTC))
  }

  implicit val yearMonthWrite = new Writes[YearMonth] {
    override def writes(o: YearMonth): JsValue = JsString(o.toString)
  }

}
