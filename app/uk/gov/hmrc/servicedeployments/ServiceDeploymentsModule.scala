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

import javax.inject.{Inject, Provider, Singleton}
import play.api.inject.Module
import play.api.{Configuration, Environment}
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.servicedeployments.tags.GitConnectorOpen

class ServiceDeploymentsModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration) =
    Seq(
      bind[GitConnectorOpen].toProvider[GitConnectorOpenProvider]
    )
}
@Singleton
class GithubApiClientOpen @Inject()(config: ServiceDeploymentsConfig) extends AbstractGithubApiClient {
  override val client = GithubApiClient(config.gitOpenApiUrl, config.gitOpenToken)
}

abstract class AbstractGithubApiClient() extends GithubApiClient {

  val client: GithubApiClient

  override lazy val orgService        = client.orgService
  override lazy val teamService       = client.teamService
  override lazy val repositoryService = client.repositoryService
  override lazy val contentsService   = client.contentsService
  override lazy val releaseService    = client.releaseService
}
@Singleton
class GitConnectorOpenProvider @Inject()(
  config: ServiceDeploymentsConfig,
  futureHelpers: FutureHelpers,
  githubApiClientOpen: GithubApiClientOpen
) extends Provider[GitConnectorOpen] {

  override def get() =
    new GitConnectorOpen(futureHelpers, githubApiClientOpen, "open")
}
