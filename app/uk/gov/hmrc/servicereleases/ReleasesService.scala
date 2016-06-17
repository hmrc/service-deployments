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

import java.time.LocalDateTime

import uk.gov.hmrc.FutureHelpers._
import uk.gov.hmrc.servicereleases.deployments.{ServiceDeployment, ServiceDeploymentsService}
import uk.gov.hmrc.servicereleases.services.{Repository, ServiceRepositoriesService}
import uk.gov.hmrc.servicereleases.tags.TagsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ReleasesService {
  def updateModel(): Future[Iterable[Boolean]]
}

class DefaultReleasesService(serviceRepositoriesService: ServiceRepositoriesService,
                              deploymentsService: ServiceDeploymentsService,
                              tagsService: TagsService,
                              repository: ReleasesRepository) extends ReleasesService {

  def updateModel() =
    for {
      serviceRepositoryDeployments <- getNewServiceRepositoryDeployments()
      tagDates <- getTagsDatesFor(serviceRepositoryDeployments.serviceName, serviceRepositoryDeployments.repositories)
      success <- storeRelease(serviceRepositoryDeployments, tagDates)
    } yield success

  private def getNewServiceRepositoryDeployments() =
    FutureIterable(
      for {
        serviceRepositories <- serviceRepositoriesService.getAll().map(_.take(10))
        knownDeployments <- deploymentsService.getAll()
        knownReleases <- repository.getAll()
      } yield serviceRepositories.map(Service(_, knownDeployments, knownReleases)))

  private def getTagsDatesFor(serviceName: String, repositories: Seq[Repository]) =
    Future.sequence(repositories.map { r => tagsService.get(r.org, serviceName, r.repoType)})
      .map(_.flatten.map { t => t.version -> t.createdAt } toMap)

  private def storeRelease(service: Service, tagDates: Map[String, LocalDateTime]) =
    FutureIterable(
      service.deployments.map { nd =>
        repository.add(Release(service.serviceName, nd.version, tagDates(nd.version), nd.releasedAt)) })

  private case class Service(serviceName: String, repositories: Seq[Repository], deployments: Seq[ServiceDeployment])
  private object Service {
    def apply(serviceRepositories: (String, Seq[Repository]),
              knownDeployments: Map[String, Seq[ServiceDeployment]],
              knownReleases: Map[String, Seq[Release]]): Service =

      serviceRepositories match {
        case (serviceName, repositories) =>
          new Service(serviceName, repositories, knownDeployments.getOrElse(serviceName, Seq())
            .filterNot(kd => knownReleases.getOrElse(serviceName, Seq()).exists(kr => kr.version == kd.version)))
      }
  }
}
