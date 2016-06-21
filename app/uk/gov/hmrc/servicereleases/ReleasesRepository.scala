/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.servicereleases

import java.time.{LocalDateTime, ZoneId, ZoneOffset}
import java.util.TimeZone

import play.api.libs.json.{JsValue, Writes, _}
import reactivemongo.api.collections.bson.BSONQueryBuilder
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

case class Release(name: String, version: String, creationDate: LocalDateTime, productionDate: LocalDateTime)

object Release {
  implicit val localDateTimeRead: Reads[LocalDateTime] =
    (__ \ "$date").read[Long].map { dateTime => LocalDateTime.ofEpochSecond(dateTime, 0, ZoneOffset.UTC) }

  implicit val localDateTimeWrite: Writes[LocalDateTime] = new Writes[LocalDateTime] {
    def writes(dateTime: LocalDateTime): JsValue = Json.obj(
      "$date" -> dateTime.atOffset(ZoneOffset.UTC).toEpochSecond
    )
  }

  val formats = Json.format[Release]
}

trait ReleasesRepository {
  def add(release: Release): Future[Boolean]
  def getAll(): Future[Map[String, Seq[Release]]]
  def getForService(serviceName: String): Future[Option[Seq[Release]]]
}

class MongoReleasesRepository(mongo: () => DB)
  extends ReactiveRepository[Release, BSONObjectID](
    collectionName = "releases",
    mongo = mongo,
    domainFormat = Release.formats) with ReleasesRepository {

  def add(release: Release): Future[Boolean] = {
    insert(release) map {
      case lastError if lastError.inError => throw lastError
      case _ => true
    }
  }

  override def getAll(): Future[Map[String, Seq[Release]]] = findAll().map { all => all.groupBy(_.name) }

  def getForService(serviceName: String): Future[Option[Seq[Release]]] = {
    find("name" -> BSONDocument("$eq" -> serviceName)) map {
      case Nil => None
      case data => Some(data)
    }
  }
}