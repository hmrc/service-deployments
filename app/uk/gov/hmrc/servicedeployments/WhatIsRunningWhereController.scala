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

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.api.mvc.Action
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.servicedeployments.{Error, Info, Scheduler, Warn}
import uk.gov.hmrc.servicedeployments.deployments.WhatIsRunningWhere
import uk.gov.hmrc.servicedeployments.deployments.WhatIsRunningWhere.format


object WhatIsRunningWhereController extends WhatIsRunningWhereController with MongoDbConnection {
  override def whatIsRunningWhereRepository = new MongoWhatIsRunningWhereRepository(db)
}

trait WhatIsRunningWhereController extends BaseController {


  def whatIsRunningWhereRepository: WhatIsRunningWhereRepository

  private def fromWhatIsRunningWhereModel(w: WhatIsRunningWhereModel): WhatIsRunningWhere =
    WhatIsRunningWhere(w.applicationName, w.environments)

  def forApplication(applicationName: String) = Action.async { implicit request =>
    whatIsRunningWhereRepository.getForApplication(applicationName).map {
      case Some(data) => Ok(Json.toJson(data.map(fromWhatIsRunningWhereModel)))
      case None => NotFound
    }
  }


  def getAll() = Action.async { implicit request =>
    whatIsRunningWhereRepository.getAll.map { deployments =>
      Ok(Json.toJson(deployments.map(fromWhatIsRunningWhereModel)))
    }
  }

  def update() = Action.async { implicit request =>
    Scheduler.updateWhatIsRunningWhereModel.map {
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
