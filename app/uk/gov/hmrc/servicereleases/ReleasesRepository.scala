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

import java.time.{LocalDateTime, ZoneOffset}

import play.api.libs.json.{JsValue, Writes, _}
import reactivemongo.api.DB
import reactivemongo.api.collections.bson.BSONQueryBuilder
import reactivemongo.api.indexes.{IndexType, Index}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository
//import uk.gov.hmrc.FutureHelpers.withTimerAndCounter
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class Release(name: String, version: String,
                   creationDate: Option[LocalDateTime], productionDate: LocalDateTime,
                   interval: Option[Long] = None, leadTime: Option[Long] = None,
                   _id: Option[BSONObjectID] = None)

object Release {
  implicit val localDateTimeRead: Reads[LocalDateTime] =
    __.read[Long].map { dateTime => LocalDateTime.ofEpochSecond(dateTime, 0, ZoneOffset.UTC) }

  implicit val localDateTimeWrite: Writes[LocalDateTime] = new Writes[LocalDateTime] {
    def writes(dateTime: LocalDateTime): JsValue = JsNumber(value = dateTime.atOffset(ZoneOffset.UTC).toEpochSecond)
  }

  implicit val bsonIdFormat = ReactiveMongoFormats.objectIdFormats

  val formats = Json.format[Release]

}

trait ReleasesRepository {
  def add(release: Release): Future[Boolean]

  def update(release: Release): Future[Boolean]

  def allServiceReleases: Future[Map[String, Seq[Release]]]

  def getAllReleases: Future[Seq[Release]]

  def getForService(serviceName: String): Future[Option[Seq[Release]]]

  def clearAllData: Future[Boolean]
}

class MongoReleasesRepository(mongo: () => DB)
  extends ReactiveRepository[Release, BSONObjectID](
    collectionName = "releases",
    mongo = mongo,
    domainFormat = Release.formats) with ReleasesRepository {


  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(Index(Seq("productionDate" -> IndexType.Descending), name = Some("productionDateIdx"))),
        collection.indexesManager.ensure(Index(Seq("name" -> IndexType.Hashed), name = Some("nameIdx")))
      )
    )

  def add(release: Release): Future[Boolean] = {
    //withTimerAndCounter("mongo.write") {
      insert(release) map {
        case lastError if lastError.inError => throw lastError
        case _ => true
      }
    //}
  }

  def update(release: Release): Future[Boolean] = {
    require(release._id.isDefined, "_id must be defined")
    //withTimerAndCounter("mongo.update") {
      collection.update(
        selector = Json.obj("_id" -> Json.toJson(release._id.get)(ReactiveMongoFormats.objectIdWrite)),
        update = Release.formats.writes(release)
      ).map {
        case lastError if lastError.inError => throw lastError
        case _ => true
      }
    //}
  }

  override def allServiceReleases: Future[Map[String, Seq[Release]]] = findAll().map { all => all.groupBy(_.name) }

  def getForService(serviceName: String): Future[Option[Seq[Release]]] = {

    //withTimerAndCounter("mongo.read") {
      find("name" -> BSONDocument("$eq" -> serviceName)) map {
        case Nil => None
        case data => Some(data.sortBy(_.productionDate.toEpochSecond(ZoneOffset.UTC)).reverse)
      }
    //}
  }

  def clearAllData = super.removeAll().map(!_.hasErrors)

  def getAllReleases: Future[Seq[Release]] = collection
    .find(BSONDocument.empty)
    .sort(Json.obj("productionDate" -> JsNumber(-1)))
    .cursor[Release]()
    .collect[List]()
}