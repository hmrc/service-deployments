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

package uk.gov.hmrc.servicedeployments

import java.nio.file.{Files, Paths}
import javax.inject.{Inject, Singleton}

import play.api.{Configuration, Logger}

@Singleton
class ServiceDeploymentsConfig @Inject()(configuration: Configuration) {

  val schedulerEnabled = configuration.getBoolean("scheduler.enabled").getOrElse(false)

  lazy val deploymentsApiBase: String = config("deployments.api.url").get
  lazy val catalogueBaseUrl: String   = config("catalogue.api.url").get

  lazy val gitOpenApiHost: String = config("git.open.host").get
  lazy val gitOpenApiUrl: String  = config("git.open.api.url").get
  lazy val gitOpenToken: String   = config("git.open.api.token").get

  lazy val gitOpenStorePath: String = storePath("open-local-git-store")

  private def config(path: String) = configuration.getString(s"$path")

  private def storePath(prefix: String) = {
    val path = config("git.client.store.path")
      .fold(Files.createTempDirectory(prefix).toString)(x => Paths.get(x).resolve(prefix).toString)

    Logger.info(s"Store Path : $path")
    path
  }

}
