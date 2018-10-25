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
      bind[GitConnectorOpen].toProvider[GitConnectorOpenProvider],
      bind[GithubApiClient].toProvider[GithubApiClientOpenProvider]
    )
}


@Singleton
class GithubApiClientOpenProvider @Inject()(config: ServiceDeploymentsConfig) extends Provider[GithubApiClient] {
  override def get(): GithubApiClient = GithubApiClient(config.gitOpenApiUrl, config.gitOpenToken)
}

@Singleton
class GitConnectorOpenProvider @Inject()(
  config: ServiceDeploymentsConfig,
  futureHelpers: FutureHelpers,
  githubApiClient: GithubApiClient
) extends Provider[GitConnectorOpen] {

  override def get() =
    new GitConnectorOpen(futureHelpers, githubApiClient, "open")
}
