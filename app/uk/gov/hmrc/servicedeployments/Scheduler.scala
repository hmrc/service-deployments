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

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import org.joda.time.Duration
import play.Logger
import play.libs.Akka
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.gitclient.Git
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}
import uk.gov.hmrc.servicedeployments.deployments.{DefaultServiceDeploymentsService, DeploymentsApiConnector, DeploymentsDataSource, WhatIsRunningWhere}
import uk.gov.hmrc.servicedeployments.services.{CatalogueConnector, DefaultServiceRepositoriesService}
import uk.gov.hmrc.servicedeployments.tags.{DefaultTagsService, GitConnector, GitHubConnector}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

sealed trait JobResult
case class Error(message: String, ex : Throwable) extends JobResult {
  Logger.error(message, ex)
}
case class Warn(message: String) extends JobResult {
  Logger.warn(message)
}
case class Info(message: String) extends JobResult {
  Logger.info(message)
}

trait DefaultSchedulerDependencies extends MongoDbConnection  {
  import ServicedeploymentsConfig._

  def deploymentsDataSource: DeploymentsDataSource

  private val enterpriseDataSource = new GitConnector(
    Git(gitEnterpriseStorePath, gitEnterpriseToken, gitEnterpriseHost, withCleanUp = true),
    GithubApiClient(gitEnterpriseApiUrl, gitEnterpriseToken),
    "enterprise")

  private val openDataSource = new GitHubConnector(
    GithubApiClient(gitOpenApiUrl, gitOpenToken),
    "open")

  val akkaSystem = Akka.system()
  lazy val deploymentsService = new DefaultDeploymentsService(
    new DefaultServiceRepositoriesService(new CatalogueConnector(catalogueBaseUrl)),
    new DefaultServiceDeploymentsService(deploymentsDataSource),
    new DefaultTagsService(enterpriseDataSource, openDataSource),
    new MongoDeploymentsRepository(db))

  lazy val whatIsRunningWhereService = new  DefaultWhatIsRunningWhereUpdateService(
    deploymentsDataSource,
    new MongoWhatIsRunningWhereRepository(db)
  )

}

trait Scheduler extends LockKeeper with DefaultMetricsRegistry{
  self: MongoDbConnection  =>

  def akkaSystem: ActorSystem
  def deploymentsDataSource: DeploymentsDataSource
  def deploymentsService: DeploymentsService
  def whatIsRunningWhereService: WhatIsRunningWhereUpdateService

  override def repo: LockRepository = LockMongoRepository(db)
  override def lockId: String = "service-deployments-scheduled-job"

  override val forceLockReleaseAfter: Duration = Duration.standardMinutes(15)

  def startUpdatingDeploymentServiceModel(interval: FiniteDuration): Unit = {
    Logger.info(s"Initialising mongo update (for DeploymentServiceModel) every $interval")

    akkaSystem.scheduler.schedule(FiniteDuration(1, TimeUnit.SECONDS), interval) {
      updateDeploymentServiceModel
    }
  }

  def startUpdatingWhatIsRunningWhereModel(interval: FiniteDuration): Unit = {
    Logger.info(s"Initialising mongo update (for WhatIsRunningWhere) every $interval")

    akkaSystem.scheduler.schedule(FiniteDuration(1, TimeUnit.SECONDS), interval) {
      updateWhatIsRunningWhereModel
    }
  }

  def updateDeploymentServiceModel: Future[JobResult] = {
    tryLock {
      Logger.info(s"Starting mongo update")

      deploymentsService.updateModel().map { result =>
        val total = result.toList.length
        val failureCount = result.count(r => !r)
        val successCount = total - failureCount

        defaultMetricsRegistry.counter("scheduler.success").inc(successCount)
        defaultMetricsRegistry.counter("scheduler.failure").inc(failureCount)

        Info(s"Added/updated $successCount deployments and encountered $failureCount failures")
      }.recover { case ex =>
        Error(s"Something went wrong during the mongo update:", ex)
      }
    } map { resultOrLocked =>
      resultOrLocked getOrElse {
        Warn("Failed to obtain lock. Another process may have it.")
      }
    }
  }
  
  def updateWhatIsRunningWhereModel: Future[JobResult] = {
    tryLock {
      Logger.info(s"Starting mongo update")

      whatIsRunningWhereService.updateModel().map { result =>
        val total = result.toList.length

        defaultMetricsRegistry.counter("scheduler.success").inc(total)

        Info(s"Added/updated $total WhatIsRunningWhere")
      }.recover { case ex =>
        Error(s"Something went wrong during the mongo update:", ex)
      }
    } map { resultOrLocked =>
      resultOrLocked getOrElse {
        Warn("Failed to obtain lock. Another process may have it.")
      }
    }
  }
}

object Scheduler extends Scheduler with DefaultSchedulerDependencies {
  import ServicedeploymentsConfig._

  override val deploymentsDataSource = new DeploymentsApiConnector(deploymentsApiBase)
}
