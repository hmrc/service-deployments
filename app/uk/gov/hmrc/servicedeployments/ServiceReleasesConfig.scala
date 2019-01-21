/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.Configuration

@Singleton
class ServiceDeploymentsConfig @Inject()(configuration: Configuration) {

  val schedulerEnabled: Boolean = configuration.getOptional[Boolean]("scheduler.enabled").getOrElse(false)

  lazy val artifactoryBase: String = configuration.get[String]("artifactory.url")
  lazy val artifactoryApiKey: Option[String] = configuration.getOptional[String]("artifactory.apikey")
  lazy val deploymentsApiBase: String = configuration.get[String]("deployments.api.url")

}
