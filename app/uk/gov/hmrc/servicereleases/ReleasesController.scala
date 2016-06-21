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

import play.api.libs.json.{Json, Writes}
import play.api.mvc.Action
import play.modules.reactivemongo.MongoDbConnection

import play.api.libs.concurrent.Execution.Implicits._

import uk.gov.hmrc.play.microservice.controller.BaseController

object ReleasesController extends BaseController with MongoDbConnection  {
  import uk.gov.hmrc.JavaDateTimeJsonFormatter._
  implicit def writes: Writes[Release] = Json.writes[Release]

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
}
