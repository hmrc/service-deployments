/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID, BSONRegex}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.servicedeployments.deployments.Deployer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Deployment(
  name: String,
  version: String,
  creationDate: Option[LocalDateTime],
  productionDate: LocalDateTime,
  interval: Option[Long]    = None,
  leadTime: Option[Long]    = None,
  deployers: Seq[Deployer]  = Seq.empty,
  _id: Option[BSONObjectID] = None)

object Deployment {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import play.api.libs.json._

  val localDateTimeRead: Reads[LocalDateTime] =
    __.read[Long].map { dateTime =>
      LocalDateTime.ofEpochSecond(dateTime, 0, ZoneOffset.UTC)
    }

  val localDateTimeToEpochSecondsWrites = new Writes[LocalDateTime] {
    def writes(dateTime: LocalDateTime): JsValue = JsNumber(value = dateTime.atOffset(ZoneOffset.UTC).toEpochSecond)
  }

  val deploymentReads: Reads[Deployment] = (
    (__ \ "name").read[String] and
      (__ \ "version").read[String] and
      (__ \ "creationDate").readNullable[LocalDateTime](localDateTimeRead) and
      (__ \ "productionDate").read[LocalDateTime](localDateTimeRead) and
      (__ \ "interval").readNullable[Long] and
      (__ \ "leadTime").readNullable[Long] and
      (__ \ "deployers").readNullable[Seq[Deployer]].map(_.getOrElse(Seq.empty)) and
      (__ \ "_id").readNullable[BSONObjectID](ReactiveMongoFormats.objectIdRead)
  )(Deployment.apply _)

  val deploymentWrites: Writes[Deployment] = {
    implicit val localDateTimeWrite: Writes[LocalDateTime] = localDateTimeToEpochSecondsWrites
    Json.writes[Deployment]
  }

  val formats = new OFormat[Deployment]() {
    override def writes(o: Deployment): JsObject = deploymentWrites.writes(o).as[JsObject]

    override def reads(json: JsValue): JsResult[Deployment] = deploymentReads.reads(json)
  }

}

@Singleton
class DeploymentsRepository @Inject()(mongo: ReactiveMongoComponent, futureHelpers: FutureHelpers)
    extends ReactiveRepository[Deployment, BSONObjectID](
      collectionName = "deployments",
      mongo          = mongo.mongoConnector.db,
      domainFormat   = Deployment.formats) {

  override def indexes: Seq[Index] =
    Seq(
      Index(
        Seq("productionDate" -> IndexType.Descending),
        name = Some("productionDateIdx")),
      Index(
        Seq("name" -> IndexType.Hashed),
        name = Some("nameIdx")))

  def add(deployment: Deployment): Future[Boolean] =
    futureHelpers.withTimerAndCounter("mongo.write") {
      insert(deployment)
        .map(_ => true)
    }.recover {
      case lastError =>
        logger.error(s"Could not insert ${deployment.name}", lastError)
        throw lastError
    }

  def update(deployment: Deployment): Future[Boolean] = {
    require(deployment._id.isDefined, "_id must be defined")
    futureHelpers.withTimerAndCounter("mongo.update") {
      collection
        .update(
          selector = Json.obj("_id" -> Json.toJson(deployment._id.get)(ReactiveMongoFormats.objectIdWrite)),
          update   = Deployment.formats.writes(deployment)
        )
        .map(_ => true)
    }.recover {
      case lastError =>
        logger.error(s"Could not update ${deployment.name}", lastError)
        throw lastError
    }
  }

  def allServiceDeployments: Future[Map[String, Seq[Deployment]]] =
    findAll()
      .map(_.groupBy(_.name))

  def deploymentsForServices(serviceNames: Set[String]): Future[Seq[Deployment]] =
    futureHelpers.withTimerAndCounter("mongo.read") {
      val serviceNamesJson =
        serviceNames.map(serviceName => toJsFieldJsValueWrapper(BSONRegex("^" + serviceName + "$", "i"))).toSeq
      find("name" -> Json.obj("$in" -> Json.arr(serviceNamesJson: _*)))
        .map(_.sortBy(_.productionDate.toEpochSecond(ZoneOffset.UTC)).reverse)
    }

  def clearAllData = super.removeAll().map(_.ok)

  def getAllDeployments: Future[Seq[Deployment]] =
    collection
      .find[BSONDocument, JsObject](BSONDocument.empty, None)
      .sort(Json.obj("productionDate" -> JsNumber(-1)))
      .cursor[Deployment]()
      .collect[List](Int.MaxValue, Cursor.FailOnError[List[Deployment]]())
}
