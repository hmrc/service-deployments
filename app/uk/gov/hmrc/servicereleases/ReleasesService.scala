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

import java.time.{Duration, LocalDateTime, ZoneOffset}

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
      service <- getNewServiceRepositoryDeployments
      _ <- log(service)
      maybeTagDates <- tryGetTagDatesFor(service)
      success <- processReleases(service, maybeTagDates)
    } yield success

  private def getNewServiceRepositoryDeployments = {
    val allKnownDeploymentsF: Future[Map[String, Seq[ServiceDeployment]]] = deploymentsService.getAll()
    val allKnownReleasesF: Future[Map[String, Seq[Release]]] = repository.getAll
    val allServiceRepositoriesF: Future[Map[String, Seq[Repository]]] = serviceRepositoriesService.getAll()

    FutureIterable(
      for {
        knownDeployments <- allKnownDeploymentsF
        knownReleases <- allKnownReleasesF
        serviceRepositories <- allServiceRepositoriesF
      } yield
      serviceRepositories.map(Service(_, knownDeployments, knownReleases))
    )

  }

  private def tryGetTagDatesFor(service: Service) =
    getTagsForNewDeployments(service).map { results =>
      combineResultsOrFailIfAnyTryDoesNotSucceed(results)
        .map(_.flatten)
        .map(_.sortBy(-_.createdAt.toEpochSecond(ZoneOffset.UTC)))
        .map(convertTagsToMap)
    }

  private def getTagsForNewDeployments(service: Service) =
    Future.sequence(service.deploymentsRequiringUpdates match {
      case Nil => Seq()
      case _ => service.repositories.map { r => tagsService.get(r.org, service.serviceName, r.repoType) }
    })

  private def combineResultsOrFailIfAnyTryDoesNotSucceed[T](xs: Seq[Try[T]]): Try[Seq[T]] =
    (Try(Seq[T]()) /: xs) { (a, b) => a flatMap (c => b map (d => c :+ d)) }

  private def convertTagsToMap(tags: Seq[Tag]) =
    tags.map { x => x.version -> x.createdAt } toMap

  private def processReleases(service: Service, maybeTagDates: Try[Map[String, LocalDateTime]]) =
    maybeTagDates match {
      case Success(td) => createReleasesFromNewDeploymentsAndTags(service, td)
      case Failure(ex) =>
        Logger.error(s"Error processing tags for ${service.serviceName}: ${ex.getMessage}", ex)
        FutureIterable(Seq(Future.successful(false)))
    }

  private def createReleasesFromNewDeploymentsAndTags(service: Service, tagDates: Map[String, LocalDateTime]): Future[Iterable[Boolean]] =
    FutureIterable(
      service.deploymentsRequiringUpdates.map { nd =>
        tagDates.get(nd.version) match {
          case Some(td) =>
            service.knownReleases.find(_.version == nd.version).fold {
              repository.add(Release(service.serviceName, nd.version, Some(td), nd.releasedAt, 1, Some(daysBetween(td, nd.releasedAt))))
            } { kr =>
              repository.update(kr.copy(leadTime = Some(daysBetween(td, nd.releasedAt))))
            }

          case None =>
            Logger.warn(s"Unable to locate git tag for ${service.serviceName} ${nd.version}")
            repository.add(Release(service.serviceName, nd.version, None, nd.releasedAt, 1))
        }
      })

  private def daysBetween(before: LocalDateTime, after: LocalDateTime): Long = {

    Math.round(Duration.between(before, after).toHours / 24d)
  }

  private case class Service(serviceName: String, repositories: Seq[Repository], deployments: Seq[ServiceDeployment], knownReleases: Seq[Release]) {

    lazy val deploymentsRequiringUpdates = deployments.filter(kd => isNewDeployment(kd) || isMissingLeadTimeInterval(kd))

    def isNewDeployment(deployment: ServiceDeployment): Boolean = !knownReleases.exists(kr => kr.version == deployment.version)

    def isMissingLeadTimeInterval(deployment: ServiceDeployment): Boolean = knownReleases.exists(kr => kr.version == deployment.version && kr.leadTime.isEmpty)
  }

  private object Service {
    def apply(serviceRepositories: (String, Seq[Repository]),
              knownDeployments: Map[String, Seq[ServiceDeployment]],
              knownReleases: Map[String, Seq[Release]]): Service =

      serviceRepositories match {
        case (serviceName, repositories) =>
          new Service(
            serviceName,
            repositories,
            knownDeployments.getOrElse(serviceName, Seq()),
            knownReleases.getOrElse(serviceName, Seq())
          )
      }
  }

  private def log(serviceRepositoryDeployments: Service): Future[Unit] = {
    Logger.debug(s"Checking new deployments for service: ${serviceRepositoryDeployments.serviceName}")

    serviceRepositoryDeployments.deployments.foreach {
      d => Logger.debug(
        s"Found unknown release ${d.version} for ${serviceRepositoryDeployments.serviceName} on ${d.releasedAt}")
    }

    Future.successful(Unit)
  }
}
