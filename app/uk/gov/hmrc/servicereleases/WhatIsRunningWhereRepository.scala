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

package uk.gov.hmrc.servicereleases

import java.time.LocalDateTime

import play.api.libs.json._
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.servicedeployments.Deployment
import uk.gov.hmrc.servicedeployments.Deployment.{deploymentReads, deploymentWrites, localDateTimeRead, localDateTimeToEpochSecondsWrites}
import uk.gov.hmrc.servicedeployments.FutureHelpers.withTimerAndCounter
import uk.gov.hmrc.servicedeployments.deployments.{Deployer, WhatIsRunningWhere}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


case class WhatIsRunningWhereModel(applicationName: String,
                                   environments: Seq[String],
                                   _id: Option[BSONObjectID] = None)

object WhatIsRunningWhereModel {
  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import play.api.libs.json._
  

  val whatIsRunningWhereReads: Reads[WhatIsRunningWhereModel] = (
    (__ \ "applicationName").read[String] and
      (__ \ "environments").read[Seq[String]] and
      (__ \ "_id").readNullable[BSONObjectID](ReactiveMongoFormats.objectIdRead)
    ) (WhatIsRunningWhereModel.apply _)


  val whatIsRunningWhereWrites: Writes[WhatIsRunningWhereModel] = {
    import ReactiveMongoFormats.objectIdWrite

    Json.writes[WhatIsRunningWhereModel]
  }

  val format = new OFormat[WhatIsRunningWhereModel]() {

    override def reads(json: JsValue): JsResult[WhatIsRunningWhereModel] = whatIsRunningWhereReads.reads(json)

    override def writes(o: WhatIsRunningWhereModel): JsObject = whatIsRunningWhereWrites.writes(o).as[JsObject]
  }

}

trait WhatIsRunningWhereRepository {

  def update(whatIsRunningWhere: WhatIsRunningWhere): Future[Boolean]

  def allGroupedByName: Future[Map[String, Seq[WhatIsRunningWhereModel]]]

  def getAll: Future[Seq[WhatIsRunningWhereModel]]

  def getForApplication(applicationName: String): Future[Option[Seq[WhatIsRunningWhereModel]]]

  def clearAllData: Future[Boolean]
}



//!@TODO: test
class MongoWhatIsRunningWhereRepository(mongo: () => DB)
  extends ReactiveRepository[WhatIsRunningWhereModel, BSONObjectID](
    collectionName = "WhatIsRunningWhere", //!@ rename to start lower case
    mongo = mongo,
    domainFormat = WhatIsRunningWhereModel.format) with WhatIsRunningWhereRepository {


  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(Index(Seq("applicationName" -> IndexType.Text), name = Some("applicationNameIdx")))
      )
    )



  def update(deployment: WhatIsRunningWhere): Future[Boolean] = {

    withTimerAndCounter("mongo.update") {
      for {
        update <- collection.update(selector = Json.obj("applicationName" -> Json.toJson(deployment.applicationName)), update = deployment, upsert = true)
      } yield update match {
        case lastError if lastError.inError => throw new RuntimeException(s"failed to persist $deployment")
        case _ => true
      }
    }
  }

  override def allGroupedByName: Future[Map[String, Seq[WhatIsRunningWhereModel]]] = {
    findAll().map { all => all.groupBy(_.applicationName) }
  }

  def getForApplication(applicationName: String): Future[Option[Seq[WhatIsRunningWhereModel]]] = {

    withTimerAndCounter("mongo.read") {
      find("applicationName" -> BSONDocument("$eq" -> applicationName)) map {
        case Nil => None
        case data => Some(data)
      }
    }
  }

  def clearAllData = super.removeAll().map(!_.hasErrors)

  def getAll: Future[Seq[WhatIsRunningWhereModel]] = {
    collection
      .find(BSONDocument.empty)
      .cursor[WhatIsRunningWhereModel]()
      .collect[List]()
  }
}
