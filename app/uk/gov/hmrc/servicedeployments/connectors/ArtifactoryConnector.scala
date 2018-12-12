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

package uk.gov.hmrc.servicedeployments.connectors
import java.time.ZonedDateTime

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import uk.gov.hmrc.HttpClient
import uk.gov.hmrc.servicedeployments.ServiceDeploymentsConfig
import uk.gov.hmrc.servicedeployments.tags.Tag

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ArtifactoryConnector {
  case class RepositoryInfo(repo: String, path: String, created: String, children: Seq[RepositoryInfoChild])
  case class RepositoryInfoChild(uri: String, folder: Boolean)

  implicit val repositoryInfoChildFormat = Json.format[RepositoryInfoChild]
  implicit val repositoryInfoFormat = Json.format[RepositoryInfo]

  def artifactFromPath(path: String) : String = {
    val pattern = """\/([a-z\-]+)_2\.11$""".r
    pattern.findFirstMatchIn(path).map(_.group(1)).getOrElse(path)
  }

  def versionFromPath(path: String) : Option[String] = {
    val pattern = """\/(\d+\.\d+.\d+)\-*.*$""".r
    pattern.findFirstMatchIn(path).map(_.group(1))
  }

}


@Singleton
class ArtifactoryConnector @Inject() (http:HttpClient, config: ServiceDeploymentsConfig){

  import ArtifactoryConnector._

  def findVersion(artifactName: String, version: String, scalaVersion: String = "2.11") : Future[Tag] = {
    val baseUri = s"${config.artifactoryBaseUri}/api/storage/hmrc-releases/uk/gov/hmrc/${artifactName}_$scalaVersion/$version"
    http.get[RepositoryInfo](baseUri).map(r => Tag(version, ZonedDateTime.parse(r.created).toLocalDateTime))
  }

}


