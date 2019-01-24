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

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID, BSONRegex}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.servicedeployments.deployments.ServiceDeploymentInformation

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class WhatIsRunningWhereModel(
  serviceName: String,
  deployments: Set[ServiceDeploymentInformation.Deployment],
  _id: Option[BSONObjectID] = None)

object WhatIsRunningWhereModel {
  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import play.api.libs.json._

  val whatIsRunningWhereReads: Reads[WhatIsRunningWhereModel] = (
    (__ \ "serviceName").read[String] and
      (__ \ "deployments").read[Set[ServiceDeploymentInformation.Deployment]] and
      (__ \ "_id").readNullable[BSONObjectID](ReactiveMongoFormats.objectIdRead)
  )(WhatIsRunningWhereModel.apply _)

  val whatIsRunningWhereWrites: OWrites[WhatIsRunningWhereModel] = {
    import ReactiveMongoFormats.objectIdWrite
    Json.writes[WhatIsRunningWhereModel]
  }

  val format = new OFormat[WhatIsRunningWhereModel]() {

    override def reads(json: JsValue): JsResult[WhatIsRunningWhereModel] = whatIsRunningWhereReads.reads(json)

    override def writes(o: WhatIsRunningWhereModel): JsObject = whatIsRunningWhereWrites.writes(o).as[JsObject]
  }
}

@Singleton
class WhatIsRunningWhereRepository @Inject()(mongo: ReactiveMongoComponent, futureHelpers: FutureHelpers)
    extends ReactiveRepository[WhatIsRunningWhereModel, BSONObjectID](
      collectionName = "WhatIsRunningWhere", //!@ rename to start lower case
      mongo          = mongo.mongoConnector.db,
      domainFormat   = WhatIsRunningWhereModel.format) {

  override def indexes: Seq[Index] =
    Seq(
      Index(
        Seq("serviceName" -> IndexType.Text),
        name = Some("serviceNameIdx")))

  //!@ change the type to be the WhatIsRunningWhereModel
  def update(deployment: ServiceDeploymentInformation): Future[Boolean] =
    futureHelpers.withTimerAndCounter("mongo.update") {
      collection
        .update(
          selector = Json.obj("serviceName" -> Json.toJson(deployment.serviceName)),
          update   = deployment,
          upsert   = true)
        .map(_ => true)
    }.recover {
      case lastError =>
        logger.error(s"Could not update WhatIsRunningWhereRepository with ${deployment.serviceName}")
        throw new RuntimeException(s"failed to persist $deployment")
    }

  def allGroupedByName: Future[Map[String, Seq[WhatIsRunningWhereModel]]] =
    findAll()
      .map(_.groupBy(_.serviceName))

  def getForService(serviceName: String): Future[Option[WhatIsRunningWhereModel]] =
    futureHelpers.withTimerAndCounter("mongo.read") {
      find("serviceName" -> BSONRegex("^" + serviceName + "$", "i")).map {
        case Nil  => None
        case data => data.headOption
      }
    }

  def clearAllData = super.removeAll().map(_.ok)

  def getAll: Future[Seq[WhatIsRunningWhereModel]] =
    collection
      .find(BSONDocument.empty)
      .cursor[WhatIsRunningWhereModel]()
      .collect[List](Int.MaxValue, Cursor.FailOnError[List[WhatIsRunningWhereModel]]())
}
