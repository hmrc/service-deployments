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

package uk.gov.hmrc.servicereleases.deployments

import java.time.{LocalDateTime, ZoneOffset}

import scala.collection.TraversableLike
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


case class ServiceDeployment(version: String, releasedAt: LocalDateTime)

trait ServiceDeploymentsService {
  def getServiceDeployments(serviceName: String): Future[Iterable[ServiceDeployment]]
}

class DefaultServiceDeploymentsService(dataSource: DeploymentsDataSource) extends ServiceDeploymentsService {

  def getServiceDeployments(serviceName: String): Future[Iterable[ServiceDeployment]] =
    dataSource.getAll.map { deployments =>
        deployments
          .filter { deployment => deployment.name == serviceName && isProductionDeployment(deployment) }
          .projectToServiceDeployment()
          .sortBy(_.releasedAt.toEpochSecond(ZoneOffset.UTC))
    }

  private def isProductionDeployment(deployment: Deployment): Boolean =
    deployment.environment.startsWith("production") || deployment.environment.startsWith("prod")

  private class DeploymentSeqWrapper(deployments: Seq[Deployment]) {
    def projectToServiceDeployment(): Seq[ServiceDeployment] =
      deployments.sortBy(_.firstSeen.toEpochSecond(ZoneOffset.UTC))
        .groupBy(_.version)
        .map { case (v, d) => ServiceDeployment(d.head.version, d.head.firstSeen) }
        .toSeq
    }

  private implicit def DeploymentTraversableWrapper(deployments: Seq[Deployment]) : DeploymentSeqWrapper =
    new DeploymentSeqWrapper(deployments)
}





