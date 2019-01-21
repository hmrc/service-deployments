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

package uk.gov.hmrc.servicedeployments.testonly

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Json, Reads}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.servicedeployments.{Error, Info, UpdateScheduler, Warn, WhatIsRunningWhereRepository}
import uk.gov.hmrc.servicedeployments.deployments.{EnvironmentMapping, ServiceDeploymentInformation}
import uk.gov.hmrc.servicedeployments.deployments.ServiceDeploymentInformation.Deployment

@Singleton
class WhatIsRunningWhereTestController @Inject()(whatIsRunningWhereRepository: WhatIsRunningWhereRepository,
                                                 updateScheduler: UpdateScheduler,
                                                 components: ControllerComponents)
  extends BackendController(components) {

  import ServiceDeploymentInformationReads.formatServiceDeploymentInformation

  def updateWithData: Action[AnyContent] = Action.async { implicit request =>
    val whatsRunningWhere = Json.fromJson[List[ServiceDeploymentInformation]](request.body.asJson.get).get

    updateScheduler.updateWhatIsRunningWhereModel(whatsRunningWhere).map {
      case Info(message) => Ok(message)
      case Warn(message) => Ok(message)
      case Error(message, ex) => InternalServerError(message)
    }
  }

}

object ServiceDeploymentInformationReads {

  implicit val formatEnvironmentMapping: Reads[EnvironmentMapping] = Json.reads[EnvironmentMapping]
  implicit val formatDeployment: Reads[Deployment] = Json.reads[Deployment]
  implicit val formatServiceDeploymentInformation: Reads[ServiceDeploymentInformation] = Json.reads[ServiceDeploymentInformation]

}
