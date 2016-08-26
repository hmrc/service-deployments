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

import java.time.LocalDateTime

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.Action
import play.modules.reactivemongo.MongoDbConnection

import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.servicereleases.deployments.{Deployment, DeploymentsDataSource}

import scala.concurrent.Future
import scala.io.Source

object ReleasesController extends BaseController with MongoDbConnection  {
  import uk.gov.hmrc.JavaDateTimeJsonFormatter._
  import reactivemongo.bson.BSONObjectID
  implicit val bsonIdFormat = reactivemongo.json.BSONFormats.BSONObjectIDFormat
  //implicit def writes: Writes[Release] = Json.writes[Release]

  implicit def writes: Writes[Release] =  (
    (__ \ "name").write[String] and
      (__ \ "version").write[String] and
      (__ \ "creationDate").writeNullable[LocalDateTime] and
      (__ \ "productionDate").write[LocalDateTime] and
      (__ \ "releaseInterval").writeNullable[Long] and
      (__ \ "leadTime").writeNullable[Long] and
      (__ \ "_id").writeNullable[BSONObjectID].contramap((_: Option[BSONObjectID]) => None)
    ) (unlift(Release.unapply))

  val releasesRepository = new MongoReleasesRepository(db)

  def forService(serviceName: String) = Action.async { implicit request =>
    releasesRepository.getForService(serviceName).map {
      case Some(data) => Ok(Json.toJson(data))
      case None => NotFound
    }
  }

  def update() = Action.async { implicit request =>
    Scheduler.run.map {
      case Info(message) => Ok(message)
      case Warn(message) => Ok(message)
      case Error(message) => InternalServerError(message)
    }
  }

  def importRaw() = Action.async(parse.temporaryFile) { request =>
    implicit val reads: Reads[Deployment] = (
      (JsPath \ "env").read[String] and
        (JsPath \ "an").read[String] and
        (JsPath \ "ver").read[String] and
        (JsPath \ "fs").read[LocalDateTime]
      )(Deployment.apply _)

    val source = Source.fromFile(request.body.file, "UTF-8")
    val jsons = for (line <- source.getLines()) yield Json.fromJson[Deployment](Json.parse(line))

    val scheduler = new Scheduler with DefaultSchedulerDependencies {
        val deploymentsDataSource = new DeploymentsDataSource {
          def getAll: Future[List[Deployment]] = Future.successful(jsons.map(_.get).toList) }}

    scheduler.run.map {
      case Info(message) => Ok(message)
      case Warn(message) => Ok(message)
      case Error(message) => InternalServerError(message)
    }
  }

  def clear() = Action.async { implicit request =>
    releasesRepository.removeAll() map { r =>
      Ok((!r.hasErrors).toString)
    }
  }
}
