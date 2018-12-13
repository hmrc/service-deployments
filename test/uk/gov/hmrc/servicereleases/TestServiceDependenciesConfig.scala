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
    "scheduler.enabled"   -> false,
    "deployments.api.url" -> "deployments.api.url",
    "artifactory.url"     -> "http://example.com"
  ) ++ overrides

  private def getForKey(key: String)         = configMap.getOrElse(key, throw new RuntimeException(s"$key is not defined"))
  private def getForKeyAsString(key: String) = getForKey(key).asInstanceOf[String]

  override val schedulerEnabled: Boolean       = getForKey("scheduler.enabled").asInstanceOf[Boolean]
  override lazy val deploymentsApiBase: String = getForKeyAsString("deployments.api.url")
  override lazy val artifactoryBase: String    = getForKeyAsString("artifactory.url")
  override lazy val artifactoryApiKey: Option[String] = None

}
