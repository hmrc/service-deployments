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

import java.time.{LocalDateTime, ZoneOffset}

import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.{HttpClient, JavaDateTimeJsonFormatter}

import scala.concurrent.Future

case class Deployer(name: String, deploymentDate: LocalDateTime)

object Deployer {

  import play.api.libs.functional.syntax._

  import play.api.libs.json._
  import JavaDateTimeJsonFormatter._

  val deployerWrites = (
    (__ \ 'name).write[String] and
    (__ \ 'deploymentDate).write[LocalDateTime](localDateTimeWrites)
    )(unlift(Deployer.unapply))

  val deployerReads = (
    (__ \ 'name).read[String] and
      (__ \ 'deploymentDate).read[LocalDateTime](localDateTimeReads)
    )(Deployer.apply _)


  implicit val format = new OFormat[Deployer]() {
    override def writes(o: Deployer): JsObject = deployerWrites.writes(o).as[JsObject]

    override def reads(json: JsValue): JsResult[Deployer] = deployerReads.reads(json)
  }
}

case class EnvironmentalDeployment(environment: String, name: String, version: String, firstSeen: LocalDateTime, deployers: Seq[Deployer] = Seq.empty)

object EnvironmentalDeployment {

  import JavaDateTimeJsonFormatter._

  private implicit val listToDeployer = new Reads[Deployer] {
    override def reads(json: JsValue): JsResult[Deployer] = {
      json match {
        case JsArray(Seq(JsString(name), time: JsNumber)) => JsSuccess(Deployer(name, time.as[LocalDateTime]))
        case _ => JsError(s"invalid json format for field deployer_audit required [[name, epoch seconds]] got ${json.toString()}")
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

  def getAll: Future[List[EnvironmentalDeployment]] = {

    Logger.info("Getting all the rdeployments.")
    HttpClient.getWithParsing(s"$deploymentsApiBase/apps?secondsago=31557600") {
      case JsArray(x) =>

        val (validRecords, inValidRecords) = x.partition { jsv =>
          (jsv \ "fs").validate[LocalDateTime].isSuccess
        }
        inValidRecords.foreach(x => Logger.warn(s"Invalid deployments record : ${x.toString()}"))
        validRecords.map(_.as[EnvironmentalDeployment]).toList

      case _ =>
        Logger.warn(s"No deployment records returned")
        Nil
    }
  }


}
