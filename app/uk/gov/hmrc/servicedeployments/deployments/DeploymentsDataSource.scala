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

package uk.gov.hmrc.servicedeployments.deployments

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.servicedeployments.ServiceDeploymentsConfig
import uk.gov.hmrc.servicedeployments.deployments.EnvironmentMapping.fullListOfEnvironments
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

case class EnvironmentalDeployment(
  environment: String,
  name: String,
  version: String,
  firstSeen: LocalDateTime,
  deployers: Seq[Deployer] = Seq.empty)

object EnvironmentalDeployment {

  import JavaDateTimeJsonFormatter._

  private implicit val listToDeployer = new Reads[Deployer] {
    override def reads(json: JsValue): JsResult[Deployer] =
      json match {
        case JsArray(Seq(JsString(name), time: JsNumber)) => JsSuccess(Deployer(name, time.as[LocalDateTime]))
        case _ =>
          JsError(
            s"invalid json format for field deployer_audit required [[name, epoch seconds]] got ${json.toString()}")
      }
  }
  implicit val reads: Reads[EnvironmentalDeployment] = (
    (JsPath \ 'env).read[String] and
      (JsPath \ 'an).read[String] and
      (JsPath \ 'ver).read[String] and
      (JsPath \ 'fs).read[LocalDateTime] and
      (JsPath \ 'deployer_audit).readNullable[List[Deployer]].map {
        case None     => Seq.empty
        case Some(ls) => ls
      }
  )(EnvironmentalDeployment.apply _)

}

final case class EnvironmentMapping(name: String, releasesAppId: String)

object EnvironmentMapping {
  implicit val environmentFormat = Json.format[EnvironmentMapping]
  val fullListOfEnvironments = Set(
    EnvironmentMapping("development", "development"),
    EnvironmentMapping("qa", "qa"),
    EnvironmentMapping("staging", "staging"),
    EnvironmentMapping("external test", "externaltest"),
    EnvironmentMapping("production", "production")
  )
}

import uk.gov.hmrc.servicedeployments.deployments.ServiceDeploymentInformation.Deployment

case class ServiceDeploymentInformation(serviceName: String, deployments: Set[Deployment])

object ServiceDeploymentInformation {

  case class Deployment(environmentMapping: EnvironmentMapping, datacentre: String, version: String)

  object Deployment {
    implicit val deploymentFormat: OFormat[Deployment] =
      Json.format[Deployment]
  }

  implicit val reads = new Reads[ServiceDeploymentInformation] {
    override def reads(serviceDeploymentJson: JsValue): JsResult[ServiceDeploymentInformation] = {
      val deploymentsByServiceName = serviceDeploymentJson.as[Map[String, String]]
      val serviceName              = deploymentsByServiceName.get("an")
      serviceName match {
        case Some(name) =>
          JsSuccess(ServiceDeploymentInformation(name, extractDeploymentInformation(deploymentsByServiceName)))
        case None =>
          JsError(s"'an' (i.e. application name) field is missing in json ${serviceDeploymentJson.toString()}")
      }
    }

    private def extractDeploymentInformation(serviceDeploymentInformation: Map[String, String]): Set[Deployment] =
      serviceDeploymentInformation
        .filterNot(_._1 == "an")
        .filter {
          case (datacentreWithEnvironment, version) =>
            val environment = datacentreWithEnvironment.split("-").head
            fullListOfEnvironments.exists(_.releasesAppId == environment)
        }
        .map {
          case (datacentreWithEnvironment, version) =>
            val environment             = datacentreWithEnvironment.split("-").head
            val datacentre              = datacentreWithEnvironment.split("-").tail.mkString("-")
            val maybeEnvironmentMapping = fullListOfEnvironments.find(_.releasesAppId == environment)

            Deployment(
              maybeEnvironmentMapping.get,
              datacentreWithEnvironment.replaceAll("^\\w+-", ""),
              version
            )
        }
        .toSet
  }

  implicit val writes = Json.writes[ServiceDeploymentInformation]

  implicit val format: Format[ServiceDeploymentInformation] =
    Format(reads, writes)

}
@ImplementedBy(classOf[ReleasesAppConnector])
trait DeploymentsDataSource {
  def getAll: Future[List[EnvironmentalDeployment]]
  def whatIsRunningWhere: Future[List[ServiceDeploymentInformation]]
}

@Singleton
class ReleasesAppConnector @Inject()(serviceDeploymentsConfig: ServiceDeploymentsConfig) extends DeploymentsDataSource {

  val deploymentsApiBase: String = serviceDeploymentsConfig.deploymentsApiBase
  def getAll: Future[List[EnvironmentalDeployment]] = {

    Logger.info("Getting all the deployments.")
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

  override def whatIsRunningWhere: Future[List[ServiceDeploymentInformation]] = {
    Logger.info("Getting whatIsRunningWhere records.")
    HttpClient.getWithParsing(s"$deploymentsApiBase/whats-running-where", List("Accept" -> "application/json")) {
      case JsArray(x) =>
        val (validRecords, inValidRecords) = x.partition { jsv =>
          jsv.validate[ServiceDeploymentInformation].isSuccess
        }
        inValidRecords.foreach(x => Logger.warn(s"Invalid whatIsRunningWhere record : ${x.toString()}"))
        validRecords.map(_.as[ServiceDeploymentInformation]).toList

      case _ =>
        Logger.warn(s"No whatIsRunningWhere records returned")
        Nil
    }

  }
}
