/*
 * Copyright 2016 HM Revenue & Customs
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

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import play.Logger
import play.libs.Akka
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.gitclient.Git
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.servicereleases.deployments.{DefaultServiceDeploymentsService, ReleasesApiConnector}
import uk.gov.hmrc.servicereleases.services.{CatalogueConnector, DefaultServiceRepositoriesService}
import uk.gov.hmrc.servicereleases.tags.{DefaultTagsService, GitConnector, GitHubConnector}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

trait Scheduler {
  def akkaSystem: ActorSystem
  def releasesService: ReleasesService

  def start(interval: FiniteDuration): Unit = {
    Logger.info(s"Initialising mongo update every $interval")
    akkaSystem.scheduler.schedule(FiniteDuration(1, TimeUnit.SECONDS), interval) {
      releasesService.updateModel().onComplete { result =>
        Logger.debug(s"Job result: ${result.isFailure}")
      }
    }
  }
}

object Scheduler extends Scheduler with MongoDbConnection {
  import ServiceReleasesConfig._

  val akkaSystem = Akka.system()

  val enterpriseDataSource = new GitConnector(
    Git(gitEnterpriseStorePath, gitEnterpriseToken, gitEnterpriseHost, withCleanUp = true),
    GithubApiClient(gitEnterpriseApiUrl, gitEnterpriseToken))

  val openDataSource = new GitHubConnector(GithubApiClient(gitOpenApiUrl, gitOpenToken))

  val releasesService = new DefaultReleasesService(
    new DefaultServiceRepositoriesService(new CatalogueConnector(catalogueBaseUrl)),
    new DefaultServiceDeploymentsService(new ReleasesApiConnector(releasesApiBase)),
    new DefaultTagsService(enterpriseDataSource, openDataSource),
    new MongoReleasesRepository(db)
  )
}
