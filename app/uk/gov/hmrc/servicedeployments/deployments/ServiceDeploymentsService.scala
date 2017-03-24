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

package uk.gov.hmrc.servicedeployments.deployments

import java.time.{LocalDateTime, ZoneOffset}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class ServiceDeployment(version: String, deploymentdAt: LocalDateTime, deployers : Seq[Deployer] = Seq.empty )


trait ServiceDeploymentsService {
  def getAll(): Future[Map[String, Seq[ServiceDeployment]]]
}

class DefaultServiceDeploymentsService(dataSource: DeploymentsDataSource) extends ServiceDeploymentsService {

  def getAll(): Future[Map[String, Seq[ServiceDeployment]]] =
    dataSource.getAll.map { deployments =>
        deployments
          .filter { deployment => isProductionDeployment(deployment) }
          .groupBy(_.name)
          .asServiceDeployments()
    }

  private def isProductionDeployment(deployment: EnvironmentalDeployment): Boolean =
    deployment.environment.startsWith("production") || deployment.environment.startsWith("prod")

  private class DeploymentMapWrapper(deployments: Map[String, Seq[EnvironmentalDeployment]]) {
    def asServiceDeployments(): Map[String, Seq[ServiceDeployment]] =
      deployments.map { case (serviceName, list) =>
        serviceName ->
          firstDeploymentForEachVersionIn(list).sortBy(_.deploymentdAt.toEpochSecond(ZoneOffset.UTC)) }

    private def firstDeploymentForEachVersionIn(deployments: Seq[EnvironmentalDeployment]) =
      deployments.sortBy(_.firstSeen.toEpochSecond(ZoneOffset.UTC))
        .groupBy(_.version)
        .map { case (v, d) => ServiceDeployment(d.head.version, d.head.firstSeen, d.head.deployers) }
        .toSeq
  }

  private implicit def DeploymentTraversableWrapper(deployments: Map[String, Seq[EnvironmentalDeployment]]) : DeploymentMapWrapper =
    new DeploymentMapWrapper(deployments)

}
