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

import play.api.Logger
import uk.gov.hmrc.FutureHelpers._
import uk.gov.hmrc.servicereleases.deployments.{ServiceDeployment, ServiceDeploymentsService}
import uk.gov.hmrc.servicereleases.services.{Repository, ServiceRepositoriesService}
import uk.gov.hmrc.servicereleases.tags.{Tag, TagsService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait ReleasesService {
  def updateModel(): Future[Iterable[Boolean]]
}

class DefaultReleasesService(serviceRepositoriesService: ServiceRepositoriesService,
                              deploymentsService: ServiceDeploymentsService,
                              tagsService: TagsService,
                              repository: ReleasesRepository) extends ReleasesService {

  def updateModel() =
    for {
      service <- getNewServiceRepositoryDeployments()
      _ <- log(service)
      maybeTagDates <- tryGetTagDatesFor(service)
      success <- processNewReleases(service, maybeTagDates)
    } yield success

  private def getNewServiceRepositoryDeployments() =
    FutureIterable(
      for {
        knownDeployments <- deploymentsService.getAll()
        knownReleases <- repository.getAll()
        serviceRepositories <- serviceRepositoriesService.getAll()
      } yield serviceRepositories
        .map(Service(_, knownDeployments, knownReleases)))

  private def tryGetTagDatesFor(service: Service) =
    getTagsForNewDeployments(service).map { results =>
      combineResultsOrFailIfAnyTryDoesNotSucceed(results)
          .map(_.flatten)
          .map(convertTagsToMap) }

  private def getTagsForNewDeployments(service: Service) =
    FutureIterable(service.newDeployments match {
      case Nil => Seq()
      case _ => service.repositories.map { r => tagsService.get(r.org, service.serviceName, r.repoType) }})

  private def combineResultsOrFailIfAnyTryDoesNotSucceed[T](xs : Iterable[Try[T]]) : Try[Iterable[T]] =
    (Try(Seq[T]()) /: xs) { (a, b) => a flatMap (c => b map (d => c :+ d)) }

  private def convertTagsToMap(tags: Iterable[Tag]) =
    tags.map { x => x.version -> x.createdAt } toMap

  private def processNewReleases(service: Service, maybeTagDates: Try[Map[String, LocalDateTime]]) =
    maybeTagDates match {
      case Success(td) => createReleasesFromNewDeploymentsAndTags(service, td)
      case Failure(ex) =>
        Logger.error(s"Error processing tags for ${service.serviceName}: ${ex.getMessage}")
        FutureIterable(Seq(Future.successful(false))) }

  private def createReleasesFromNewDeploymentsAndTags(service: Service, tagDates: Map[String, LocalDateTime]) =
    FutureIterable(
      service.newDeployments.map { nd =>
        tagDates.get(nd.version) match {
          case Some(td) =>
            repository.add(Release(service.serviceName, nd.version, Some(tagDates(nd.version)), nd.releasedAt))
          case None =>
            Logger.warn(s"Unable to locate git tag for ${service.serviceName} ${nd.version}")
            repository.add(Release(service.serviceName, nd.version, None, nd.releasedAt)) }})

  private case class Service(serviceName: String, repositories: Seq[Repository], newDeployments: Seq[ServiceDeployment])
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

  private def log(serviceRepositoryDeployments: Service): Future[Unit] = {
    Logger.debug(s"Checking new deployments for service: ${serviceRepositoryDeployments.serviceName}")

    serviceRepositoryDeployments.newDeployments.foreach {
      d => Logger.debug(
        s"Found unknown release ${d.version} for ${serviceRepositoryDeployments.serviceName} on ${d.releasedAt}")}

    Future.successful(Unit)
  }
}
