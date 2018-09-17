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

import java.time.LocalDateTime

import javax.inject.{Inject, Singleton}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import play.api.mvc.Action
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.servicedeployments.deployments.{Deployer, DeploymentsDataSource, EnvironmentalDeployment, ServiceDeploymentInformation}

import scala.concurrent.Future
import scala.io.Source

case class DeploymentResult(
  name: String,
  version: String,
  creationDate: Option[LocalDateTime],
  productionDate: LocalDateTime,
  interval: Option[Long],
  leadTime: Option[Long],
  deployers: Seq[Deployer])

object DeploymentResult {

  import uk.gov.hmrc.JavaDateTimeJsonFormatter._

  implicit val formats = Json.format[DeploymentResult]

  def fromDeployment(deployment: Deployment): DeploymentResult =
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

@Singleton
class DeploymentsController @Inject()(updateScheduler: UpdateScheduler, deploymentsRepository: DeploymentsRepository)
    extends BaseController {

  def getAll = Action.async { implicit request =>
    deploymentsRepository.getAllDeployments map {
      case Nil         => Ok("{}")
      case deployments => Ok(toJson(deployments map DeploymentResult.fromDeployment))
    }
  }

  def forService(serviceName: String) = Action.async { implicit request =>
    deploymentsRepository.deploymentsForServices(Set(serviceName)) map {
      case Nil         => NotFound
      case deployments => Ok(toJson(deployments map DeploymentResult.fromDeployment))
    }
  }

  def forServices = Action.async(parse.json) { implicit request =>
    withJsonBody[Set[String]] {
      case serviceNames if serviceNames.isEmpty =>
        Future.successful(BadRequest("List of service names required"))
      case serviceNames =>
        deploymentsRepository.deploymentsForServices(serviceNames) map {
          case Nil         => Ok{"{}"}
          case deployments => Ok(toJson(deployments map DeploymentResult.fromDeployment))
        }
    }
  }

  def update() = Action.async { implicit request =>
    updateScheduler.updateDeploymentServiceModel.map {
      case Info(message)      => Ok(message)
      case Warn(message)      => Ok(message)
      case Error(message, ex) => InternalServerError(message)
    }
  }

  def importRaw() = Action.async(parse.temporaryFile) { request =>
    val deploymentsDataSource = new DeploymentsDataSource {

      import EnvironmentalDeployment._

      val source = Source.fromFile(request.body.file, "UTF-8")
      val jsons  = for (line <- source.getLines()) yield Json.fromJson[EnvironmentalDeployment](Json.parse(line))

      override def getAll: Future[List[EnvironmentalDeployment]] = Future.successful(jsons.map(_.get).toList)

      // noop
      override def whatIsRunningWhere: Future[List[ServiceDeploymentInformation]] = Future.successful(Nil)
    }

    updateScheduler.copy(deploymentsDataSource = deploymentsDataSource).updateDeploymentServiceModel.map {
      case Info(message)      => Ok(message)
      case Warn(message)      => Ok(message)
      case Error(message, ex) => InternalServerError(message)
    }
  }

  def clear() = Action.async { implicit request =>
    deploymentsRepository.clearAllData map { r =>
      Ok(r.toString)
    }
  }
}
