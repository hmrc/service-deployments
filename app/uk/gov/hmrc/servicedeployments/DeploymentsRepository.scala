/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.servicedeployments.deployments.Deployer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class Deployment(name: String, version: String,
                      creationDate: Option[LocalDateTime], productionDate: LocalDateTime,
                      interval: Option[Long] = None, leadTime: Option[Long] = None,
                      deployers: Seq[Deployer] = Seq.empty,
                      _id: Option[BSONObjectID] = None
                     )

object Deployment {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import play.api.libs.json._

  val localDateTimeRead: Reads[LocalDateTime] =
    __.read[Long].map { dateTime => LocalDateTime.ofEpochSecond(dateTime, 0, ZoneOffset.UTC) }

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
    ) (Deployment.apply _)


  val deploymentWrites: Writes[Deployment] = {
    import ReactiveMongoFormats.objectIdWrite
    implicit val localDateTimeWrite: Writes[LocalDateTime] = localDateTimeToEpochSecondsWrites
      Json.writes[Deployment]
  }


  val formats = new OFormat[Deployment]() {
    override def writes(o: Deployment): JsObject = deploymentWrites.writes(o).as[JsObject]

    override def reads(json: JsValue): JsResult[Deployment] = deploymentReads.reads(json)
  }


}

//trait DeploymentsRepository {
//  def add(deployment: Deployment): Future[Boolean]
//
//  def update(deployment: Deployment): Future[Boolean]
//
//  def allServicedeployments: Future[Map[String, Seq[Deployment]]]
//
//  def getAllDeployments: Future[Seq[Deployment]]
//
//  def getForService(serviceName: String): Future[Option[Seq[Deployment]]]
//
//  def clearAllData: Future[Boolean]
//}

@Singleton
class DeploymentsRepository @Inject()(mongo: ReactiveMongoComponent, futureHelpers: FutureHelpers )
  extends ReactiveRepository[Deployment, BSONObjectID](
    collectionName = "deployments",
    mongo = mongo.mongoConnector.db,
    domainFormat = Deployment.formats) {


  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(Index(Seq("productionDate" -> IndexType.Descending), name = Some("productionDateIdx"))),
        collection.indexesManager.ensure(Index(Seq("name" -> IndexType.Hashed), name = Some("nameIdx")))
      )
    )

  def add(deployment: Deployment): Future[Boolean] = {
    futureHelpers.withTimerAndCounter("mongo.write") {
      insert(deployment) map {
        case _ => true
      }
    } recover {
      case lastError =>
        logger.error(s"Could not insert ${deployment.name}", lastError)
        throw lastError
    }
  }

  def update(deployment: Deployment): Future[Boolean] = {
    require(deployment._id.isDefined, "_id must be defined")
    futureHelpers.withTimerAndCounter("mongo.update") {
      collection.update(
        selector = Json.obj("_id" -> Json.toJson(deployment._id.get)(ReactiveMongoFormats.objectIdWrite)),
        update = Deployment.formats.writes(deployment)
      ).map {
        case _ => true
      }
    } recover {
      case lastError =>
        logger.error(s"Could not update ${deployment.name}", lastError)
        throw lastError
    }
  }

  def allServicedeployments: Future[Map[String, Seq[Deployment]]] = {
    findAll().map { all => all.groupBy(_.name) }
  }

  def getForService(serviceName: String): Future[Option[Seq[Deployment]]] = {

    futureHelpers.withTimerAndCounter("mongo.read") {
      find("name" -> BSONDocument("$eq" -> serviceName)) map {
        case Nil => None
        case data => Some(data.sortBy(_.productionDate.toEpochSecond(ZoneOffset.UTC)).reverse)
      }
    }
  }

  def clearAllData = super.removeAll().map(_.ok)

  def getAllDeployments: Future[Seq[Deployment]] = {
    collection
      .find(BSONDocument.empty)
      .sort(Json.obj("productionDate" -> JsNumber(-1)))
      .cursor[Deployment]()
      .collect[List]()
  }
}
