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

import java.time.{LocalDateTime, Period}

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.Action
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.servicedeployments.deployments.{Deployer, DeploymentsDataSource, EnvironmentalDeployment, WhatIsRunningWhere}

import scala.concurrent.Future
import scala.io.Source


case class DeploymentResult(name: String, version: String,
                         creationDate: Option[LocalDateTime], productionDate: LocalDateTime,
                         interval: Option[Long], leadTime: Option[Long], deployers : Seq[Deployer])

object DeploymentResult {

  import uk.gov.hmrc.JavaDateTimeJsonFormatter._

  implicit val formats = Json.format[DeploymentResult]

  def fromDeployment(deployment: Deployment): DeploymentResult = {
    DeploymentResult(
      deployment.name,
      deployment.version,
      deployment.creationDate,
      deployment.productionDate,
      deployment.interval,
      deployment.leadTime,
      deployment.deployers
    )
  }

}

object DeploymentsController extends DeploymentsController with MongoDbConnection {
  override def deploymentsRepository = new MongoDeploymentsRepository(db)
}

trait DeploymentsController extends BaseController {

  import uk.gov.hmrc.JavaDateTimeJsonFormatter._

  def deploymentsRepository: DeploymentsRepository

  def forService(serviceName: String) = Action.async { implicit request =>
    deploymentsRepository.getForService(serviceName).map {
      case Some(data) => Ok(Json.toJson(data.map(DeploymentResult.fromDeployment)))
      case None => NotFound
    }
  }

  def getAll() = Action.async { implicit request =>
    deploymentsRepository.getAllDeployments.map { deployments =>
      Ok(Json.toJson(deployments.map(DeploymentResult.fromDeployment)))
    }

  }

  def update() = Action.async { implicit request =>
    new UpdateScheduler("service-deployments-scheduled-job").updateDeploymentServiceModel.map {
      case Info(message) => Ok(message)
      case Warn(message) => Ok(message)
      case Error(message, ex) => InternalServerError(message)
    }
  }

  def importRaw() = Action.async(parse.temporaryFile) { request =>

    import EnvironmentalDeployment._

    val source = Source.fromFile(request.body.file, "UTF-8")
    val jsons = for (line <- source.getLines()) yield Json.fromJson[EnvironmentalDeployment](Json.parse(line))

    val scheduler = new Scheduler with DefaultSchedulerDependencies {
      override def lockId: String = "service-deployments-scheduled-job"

      override val deploymentsDataSource:DeploymentsDataSource = new DeploymentsDataSource {
        override def getAll: Future[List[EnvironmentalDeployment]] = Future.successful(jsons.map(_.get).toList)

        // noop
        override def whatIsRunningWhere: Future[List[WhatIsRunningWhere]] = Future.successful(Nil)
      }
    }

    scheduler.updateDeploymentServiceModel.map {
      case Info(message) => Ok(message)
      case Warn(message) => Ok(message)
      case Error(message, ex) => InternalServerError(message)
    }
  }

  def clear() = Action.async { implicit request =>
    deploymentsRepository.clearAllData map { r =>
      Ok(r.toString)
    }
  }
}
