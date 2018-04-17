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

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import com.kenshoo.play.metrics.Metrics
import org.joda.time.Duration
import play.Logger
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}
import uk.gov.hmrc.servicedeployments.deployments.DeploymentsDataSource

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

sealed trait JobResult

case class Error(message: String, ex: Throwable) extends JobResult {
  Logger.error(message, ex)
}

case class Warn(message: String) extends JobResult {
  Logger.warn(message)
}

case class Info(message: String) extends JobResult {
  Logger.info(message)
}
@Singleton
case class UpdateScheduler @Inject()(
  deploymentsDataSource: DeploymentsDataSource,
  akkaSystem: ActorSystem,
  reactiveMongoComponent: ReactiveMongoComponent,
  metrics: Metrics,
  whatIsRunningWhereService: WhatIsRunningWhereUpdateService,
  deploymentsService: DeploymentsService) {

  val defaultMetricsRegistry = metrics.defaultRegistry

  def lockRepository: LockRepository = LockMongoRepository(reactiveMongoComponent.mongoConnector.db)

  val lockTimeout: Duration = Duration.standardMinutes(15)

  val whatsRunningWhereLock  = buildLock(lockRepository, "what-is-running-where-job", lockTimeout)
  val serviceDeploymentsLock = buildLock(lockRepository, "service-deployments-scheduled-job", lockTimeout)

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

  def updateDeploymentServiceModel: Future[JobResult] =
    serviceDeploymentsLock.tryLock {
      Logger.info(s"Starting mongo update")

      deploymentsService
        .updateModel()
        .map { result =>
          val total        = result.toList.length
          val failureCount = result.count(r => !r)
          val successCount = total - failureCount

          defaultMetricsRegistry.counter("scheduler.success").inc(successCount)
          defaultMetricsRegistry.counter("scheduler.failure").inc(failureCount)

          Info(s"Added/updated $successCount deployments and encountered $failureCount failures")
        }
        .recover {
          case ex =>
            Error(s"Something went wrong during the mongo update:", ex)
        }
    } map { resultOrLocked =>
      resultOrLocked getOrElse {
        Warn("Failed to obtain lock. Another process may have it.")
      }
    }

  def updateWhatIsRunningWhereModel: Future[JobResult] =
    whatsRunningWhereLock.tryLock {
      Logger.info(s"Starting mongo update")

      whatIsRunningWhereService
        .updateModel()
        .map { result =>
          val total = result.toList.length

          defaultMetricsRegistry.counter("scheduler.success").inc(total)

          Info(s"Added/updated $total WhatIsRunningWhere")
        }
        .recover {
          case ex =>
            Error(s"Something went wrong during the mongo update:", ex)
        }
    } map { resultOrLocked =>
      resultOrLocked getOrElse {
        Warn("Failed to obtain lock. Another process may have it.")
      }
    }

  def buildLock(lockRepository: LockRepository, theLockId: String, lockTimeout: Duration): LockKeeper =
    new LockKeeper {
      override def repo = lockRepository

      override def lockId = theLockId

      override val forceLockReleaseAfter: Duration = lockTimeout
    }
}
