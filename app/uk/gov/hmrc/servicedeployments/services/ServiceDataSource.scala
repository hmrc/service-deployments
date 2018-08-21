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

package uk.gov.hmrc.servicedeployments.services

import javax.inject.{Inject, Singleton}

import play.api.Mode.Mode
import play.api.{Configuration, Environment, Logger}
import play.api.libs.json.Json
import uk.gov.hmrc.HttpClient._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.servicedeployments.ServiceDeploymentsConfig

case class GithubUrl(name: String, url: String)
case class Service(name: String, githubUrl: GithubUrl)

@Singleton
class TeamsAndRepositoriesConnector @Inject()(
  serviceDeploymentsConfig: ServiceDeploymentsConfig,
  override val runModeConfiguration: Configuration,
  environment: Environment)
    extends ServicesConfig {

  override protected def mode: Mode = environment.mode

  implicit val urlReads = Json.reads[GithubUrl]
  implicit val reads    = Json.reads[Service]

  def getAll() = {
    Logger.info("Getting details of all the services.")
    get[List[Service]](s"${baseUrl("teams-and-repositories")}/api/services?details=true")
  }
}
