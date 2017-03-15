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

package uk.gov.hmrc.servicedeployments.deployments

import java.time.LocalDateTime

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.{HttpClient, JavaDateTimeJsonFormatter}

import scala.concurrent.Future

case class Deployer(name: String, deploymentDate: LocalDateTime)

case class EnvironmentalDeployment(environment: String, name: String, version: String, firstSeen: LocalDateTime, deployerAudit: Seq[Deployer] = Seq.empty)

object EnvironmentalDeployment {

  import JavaDateTimeJsonFormatter._

 private implicit val listToDeployer = new Reads[Deployer] {
    override def reads(json: JsValue): JsResult[Deployer] = {
      json match {
        case JsArray(Seq(JsString(name), time: JsNumber)) => JsSuccess(Deployer(name, time.as[LocalDateTime]))
      }

    }


  }
  implicit val reads: Reads[EnvironmentalDeployment] = (
    (JsPath \ 'env).read[String] and
      (JsPath \ 'an).read[String] and
      (JsPath \ 'ver).read[String] and
      (JsPath \ 'fs).read[LocalDateTime] and
      (JsPath \ 'deployer_audit).readNullable[List[Deployer]].map {
        case None => Seq.empty
        case Some(ls) => ls
      }
    ) (EnvironmentalDeployment.apply _)

}

trait DeploymentsDataSource {
  def getAll: Future[List[EnvironmentalDeployment]]
}

class DeploymentsApiConnector(deploymentsApiBase: String) extends DeploymentsDataSource {

  def getAll: Future[List[EnvironmentalDeployment]] = HttpClient.get[List[EnvironmentalDeployment]](s"$deploymentsApiBase/apps")
}
