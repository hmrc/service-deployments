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

package uk.gov.hmrc.servicereleases.deployments

import java.time.LocalDateTime

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}
import uk.gov.hmrc.{HttpClient, JavaDateTimeJsonFormatter}

import scala.concurrent.Future

case class Deployment(environment: String, name: String, version: String, firstSeen: LocalDateTime)

trait DeploymentsDataSource {
  def getAll: Future[List[Deployment]]
}

class ReleasesApiConnector(releasesApiBase: String) extends DeploymentsDataSource {
  import JavaDateTimeJsonFormatter._

  implicit val reads: Reads[Deployment] = (
    (JsPath \ "env").read[String] and
    (JsPath \ "an").read[String] and
    (JsPath \ "ver").read[String] and
    (JsPath \ "fs").read[LocalDateTime]
  )(Deployment.apply _)

  def getAll: Future[List[Deployment]] = HttpClient.get[List[Deployment]](s"$releasesApiBase/apps")
}