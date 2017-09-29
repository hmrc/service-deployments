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

import javax.inject.{Inject, Singleton}

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.api.mvc.Action
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.servicedeployments.deployments.ServiceDeploymentInformation
import uk.gov.hmrc.servicedeployments.deployments.ServiceDeploymentInformation.format


@Singleton
class WhatIsRunningWhereController @Inject() (whatIsRunningWhereRepository :WhatIsRunningWhereRepository, updateScheduler: UpdateScheduler) extends BaseController {

  private def fromWhatIsRunningWhereModel(w: WhatIsRunningWhereModel): ServiceDeploymentInformation =
    ServiceDeploymentInformation(w.serviceName, w.deployments)

  def forApplication(serviceName: String) = Action.async { implicit request =>
    whatIsRunningWhereRepository.getForService(serviceName).map {
      case Some(data) =>
        val filteredData = data.copy(deployments = data.deployments.filterNot(_.datacentre.contains("datacentred")))
        Ok(Json.toJson(fromWhatIsRunningWhereModel(filteredData)))
      case _ => NotFound
    }
  }


  def getAll() = Action.async { implicit request =>
    whatIsRunningWhereRepository.getAll.map { deployments =>
      val filteredDeployments = deployments.map { data =>
        data.copy(deployments = data.deployments.filterNot(_.datacentre.contains("datacentred")))
      }
      Ok(Json.toJson(filteredDeployments.map(fromWhatIsRunningWhereModel)))
    }
  }

  def update() = Action.async { implicit request =>
    updateScheduler.updateWhatIsRunningWhereModel.map {
      case Info(message) => Ok(message)
      case Warn(message) => Ok(message)
      case Error(message, ex) => InternalServerError(message)
    }
  }


  def clear() = Action.async { implicit request =>
    whatIsRunningWhereRepository.clearAllData map { r =>
      Ok(r.toString)
    }
  }
}
