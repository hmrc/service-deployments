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
import java.util.ServiceConfigurationError

import play.api.Logger
import FutureHelpers._
import uk.gov.hmrc.servicereleases.ReleaseOperation.{Update, Add}
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
      service <- getServiceRepositoryDeployments
      _ <- log(service)
      maybeTagDates <- tryGetTagDatesFor(service)
      success <- processReleases(service, maybeTagDates)
    } yield success

  private def getServiceRepositoryDeployments = {
    val allKnownDeploymentsF: Future[Map[String, Seq[ServiceDeployment]]] = deploymentsService.getAll()
    val allKnownReleasesF: Future[Map[String, Seq[Release]]] = repository.allServiceReleases
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
    getTagsForService(service).map { results =>
      combineResultsOrFailIfAnyTryDoesNotSucceed(results)
        .map(_.flatten)
        .map(_.sortBy(-_.createdAt.toEpochSecond(ZoneOffset.UTC)))
        .map(convertTagsToMap)
    }

  private def getTagsForService(service: Service) =
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
      case Success(td) => createOrUpdateReleasesFromDeploymentsAndTags(service, td)
      case Failure(ex) =>
        Logger.error(s"Error processing tags for ${service.serviceName}: ${ex.getMessage}", ex)
        FutureIterable(Seq(Future.successful(false)))
    }

  private def createOrUpdateReleasesFromDeploymentsAndTags(service: Service, tagDates: Map[String, LocalDateTime]): Future[Iterable[Boolean]] = {

    FutureIterable(
      new ReleaseAndOperation(service, tagDates).get.map {
        case (Add, r) =>
          Logger.info(s"Adding release : ${r.version} for service ${r.name}")
          repository.add(r)
        case (Update, r) =>
          Logger.info(s"Updating release : ${r.version} for service ${r.name}")
          repository.update(r)
      }
    )

  }


  private def log(serviceRepositoryDeployments: Service): Future[Unit] = {
    Logger.debug(s"Checking deployments for service: ${serviceRepositoryDeployments.serviceName}")
    Logger.debug(s"total deployments for ${serviceRepositoryDeployments.serviceName} : ${serviceRepositoryDeployments.deployments.size}")
    Logger.debug(s"total known releases for ${serviceRepositoryDeployments.serviceName} : ${serviceRepositoryDeployments.knownReleases.size}")
    Logger.debug(s"total deploymentsRequiringUpdates for ${serviceRepositoryDeployments.serviceName} : ${serviceRepositoryDeployments.deploymentsRequiringUpdates.size}")

    serviceRepositoryDeployments.deploymentsRequiringUpdates.foreach {
      d => Logger.debug(
        s"release ${d.version} for ${serviceRepositoryDeployments.serviceName} on ${d.releasedAt} needs update")
    }

    Future.successful(Unit)
  }

}

case class Service(serviceName: String, repositories: Seq[Repository], deployments: Seq[ServiceDeployment], knownReleases: Seq[Release]) {

  import uk.gov.hmrc.JavaDateTimeHelper._


  private lazy val newDeployments = deployments.filter(isNewDeployment)

  private lazy val allDeployments = newDeployments ++ knownReleases.map(x => ServiceDeployment(x.version, x.productionDate))

  lazy val deploymentsRequiringUpdates = allDeployments.filter(x => isNewDeployment(x) || isMissingLeadTime(x))

  private lazy val deploymentsSortedByReleasedAt = allDeployments.sortBy(_.releasedAt.toEpochSecond(ZoneOffset.UTC))

  private lazy val serviceReleaseIntervals: Seq[(String, Long)] =
    (deploymentsSortedByReleasedAt, deploymentsSortedByReleasedAt drop 1).zipped.map { case (d1, d2) =>
      (d2.version, daysBetween(d1.releasedAt, d2.releasedAt))
    }

  def releaseInterval(version: String): Option[Long] = serviceReleaseIntervals.find(_._1 == version).map(_._2)

  private def isNewDeployment(deployment: ServiceDeployment): Boolean = !knownReleases.exists(kr => kr.version == deployment.version)

  private def isMissingLeadTime(deployment: ServiceDeployment): Boolean = knownReleases.exists(kr => kr.version == deployment.version && kr.leadTime.isEmpty)

}

object Service {
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

object ReleaseOperation extends Enumeration {
  val Add, Update = Value
}

class ReleaseAndOperation(service: Service, tagDates: Map[String, LocalDateTime]) {

  import uk.gov.hmrc.JavaDateTimeHelper._

  def get: Seq[(ReleaseOperation.Value, Release)] = {

    service.deploymentsRequiringUpdates.map { nd =>
      val tagDate = tagDates.get(nd.version)
      service.knownReleases.find(_.version == nd.version).fold {
        (Add, Release(service.serviceName, nd.version, tagDate, nd.releasedAt, service.releaseInterval(nd.version), leadTime(nd, tagDate)))
      } { kr =>
        (Update, kr.copy(leadTime = leadTime(nd, tagDate), interval = service.releaseInterval(nd.version)))
      }
    }

  }

  def leadTime(nd: ServiceDeployment, tagDate: Option[LocalDateTime]): Option[Long] = {
    tagDate.map(daysBetween(_, nd.releasedAt))
  }

}

