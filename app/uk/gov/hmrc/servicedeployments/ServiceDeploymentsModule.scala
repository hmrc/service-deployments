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

package uk.gov.hmrc.servicedeployments

import javax.inject.{Inject, Named, Provider, Singleton}

import play.api.inject.Module
import play.api.{Configuration, Environment}
import uk.gov.hmrc.gitclient.{Git, GitClient}
import uk.gov.hmrc.githubclient.{GhRepoRelease, GithubApiClient}
import uk.gov.hmrc.servicedeployments.tags.{GitConnectorEnterprise, GitConnectorOpen}

import scala.concurrent.{ExecutionContext, Future}


class ServiceDeploymentsModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration) =
    Seq(
      bind[GitClient].toProvider[GitClientProvider],
      bind[GitConnectorOpen].toProvider[GitConnectorOpenProvider],
      bind[GitConnectorEnterprise].toProvider[GitConnectorEnterpriseProvider]
    )
}


@Singleton
class GitClientProvider @Inject()(config: ServiceDeploymentsConfig) extends Provider[GitClient] {

  import config._

  override def get() = Git(gitEnterpriseStorePath, gitEnterpriseToken, gitEnterpriseHost, withCleanUp = true)
}


@Singleton
class GithubApiClientOpen @Inject()(config: ServiceDeploymentsConfig) extends AbstractGithubApiClient {
  override val client =  GithubApiClient(config.gitOpenApiUrl, config.gitOpenToken)
}


@Singleton
class GithubApiClientEnterprise @Inject()(config: ServiceDeploymentsConfig) extends AbstractGithubApiClient {
  override val client =  GithubApiClient(config.gitEnterpriseApiUrl, config.gitEnterpriseToken)
}

abstract class AbstractGithubApiClient() extends GithubApiClient {

  val client: GithubApiClient

  override lazy val orgService = client.orgService
  override lazy val teamService = client.teamService
  override lazy val repositoryService = client.repositoryService
  override lazy val contentsService = client.contentsService
  override lazy val releaseService = client.releaseService
}



@Singleton
class GitConnectorOpenProvider @Inject()(config: ServiceDeploymentsConfig,
                                         futureHelpers: FutureHelpers,
                                         githubApiClientOpen: GithubApiClientOpen) extends Provider[GitConnectorOpen] {

  override def get() =
    new GitConnectorOpen(futureHelpers, githubApiClientOpen, "open")
}

@Singleton
class GitConnectorEnterpriseProvider @Inject()(config: ServiceDeploymentsConfig,
                                               gitClient: GitClient,
                                               futureHelpers: FutureHelpers,
                                               githubApiClientEnterprise: GithubApiClientEnterprise) extends Provider[GitConnectorEnterprise] {


  override def get() =
    new GitConnectorEnterprise(futureHelpers, gitClient, githubApiClientEnterprise, "enterprise")
}
