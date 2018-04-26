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

package uk.gov.hmrc.servicereleases

import play.api.Configuration
import uk.gov.hmrc.servicedeployments.ServiceDeploymentsConfig

class TestServiceDependenciesConfig(overrides: Map[String, Any] = Map())
    extends ServiceDeploymentsConfig(Configuration()) {

  val configMap = Map(
    "scheduler.enabled"              -> false,
    "deployments.api.url"            -> "deployments.api.url",
    "teams-and-repositories.api.url" -> "teams-and-repositories.api.url",
    "git.open.host"                  -> "git.open.host",
    "git.open.api.url"               -> "https://www.github.com",
    "git.open.api.token"             -> "git.open.token"
  ) ++ overrides

  private def getForKey(key: String)         = configMap.getOrElse(key, throw new RuntimeException(s"$key is not defined"))
  private def getForKeyAsString(key: String) = getForKey(key).asInstanceOf[String]

  override val schedulerEnabled: Boolean                   = getForKey("scheduler.enabled").asInstanceOf[Boolean]
  override lazy val deploymentsApiBase: String             = getForKeyAsString("deployments.api.url")
  override lazy val teamsAndRepositoriesApiBaseUrl: String = getForKeyAsString("teams-and-repositories.api.url")
  override lazy val gitOpenApiHost: String                 = getForKeyAsString("git.open.host")
  override lazy val gitOpenApiUrl: String                  = getForKeyAsString("git.open.api.url")
  override lazy val gitOpenToken: String                   = getForKeyAsString("git.open.api.token")

}
