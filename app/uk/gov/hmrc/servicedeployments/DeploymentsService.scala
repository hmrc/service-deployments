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

import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}

import play.api.Logger
import uk.gov.hmrc.servicedeployments.DeploymentOperation.{Add, Update}
import uk.gov.hmrc.servicedeployments.FutureHelpers.FutureIterable
import uk.gov.hmrc.servicedeployments.deployments.{ServiceDeployment, ServiceDeploymentsService}
import uk.gov.hmrc.servicedeployments.services.{Repository, ServiceRepositoriesService}
import uk.gov.hmrc.servicedeployments.tags.{TagsService, Tag}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

//trait DeploymentsService {
//  def updateModel(): Future[Iterable[Boolean]]
//}


@Singleton
class DeploymentsService @Inject()(serviceRepositoriesService: ServiceRepositoriesService,
                                   deploymentsService: ServiceDeploymentsService,
                                   tagsService: TagsService,
                                   repository: DeploymentsRepository) {

  def updateModel(): Future[Iterable[Boolean]] =
    for {
      service <- getServiceRepositoryDeployments
      _ <- log(service)
      maybeTagDates <- tryGetTagDatesFor(service)
      success <- processDeployments(service, maybeTagDates)
    } yield success


  private def getServiceRepositoryDeployments = {
    val allKnownDeploymentsF: Future[Map[String, Seq[ServiceDeployment]]] = deploymentsService.getAll()
    val allKnownReleasesF: Future[Map[String, Seq[Deployment]]] = repository.allServicedeployments
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

  private def tryGetTagDatesFor(service: Service): Future[Try[Map[String, LocalDateTime]]] =
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

  private def processDeployments(service: Service, maybeTagDates: Try[Map[String, LocalDateTime]]) =
    maybeTagDates match {
      case Success(td) => createOrUpdateDeploymentsFromDeploymentsAndTags(service, td)
      case Failure(ex) =>
        Logger.error(s"Error processing tags for ${service.serviceName}: ${ex.getMessage}", ex)
        FutureIterable(Seq(Future.successful(false)))
    }

  private def createOrUpdateDeploymentsFromDeploymentsAndTags(service: Service, tagDates: Map[String, LocalDateTime]): Future[Iterable[Boolean]] = {

    FutureIterable(
      new DeploymentAndOperation(service, tagDates).get.map {
        case (Add, r) =>
          Logger.info(s"Adding deployment : ${r.version} for service ${r.name}")
          repository.add(r)
        case (Update, r) =>
          Logger.info(s"Updating deployment : ${r.version} for service ${r.name}")
          repository.update(r)
      }
    )

  }


  private def log(serviceRepositoryDeployments: Service): Future[Unit] = {
    Logger.debug(s"Checking deployments for service: ${serviceRepositoryDeployments.serviceName}")
    Logger.debug(s"total deployments for ${serviceRepositoryDeployments.serviceName} : ${serviceRepositoryDeployments.deployments.size}")
    Logger.debug(s"total known deployments for ${serviceRepositoryDeployments.serviceName} : ${serviceRepositoryDeployments.knownDeployments.size}")
    Logger.debug(s"total deploymentsRequiringUpdates for ${serviceRepositoryDeployments.serviceName} : ${serviceRepositoryDeployments.deploymentsRequiringUpdates.size}")

    serviceRepositoryDeployments.deploymentsRequiringUpdates.foreach {
      d =>
        Logger.debug(
          s"deployment ${d.version} for ${serviceRepositoryDeployments.serviceName} on ${d.deploymentdAt} needs update")
    }

    Future.successful(Unit)
  }

}

case class Service(serviceName: String, repositories: Seq[Repository], deployments: Seq[ServiceDeployment], knownDeployments: Seq[Deployment]) {

  import uk.gov.hmrc.JavaDateTimeHelper._


  private lazy val newDeployments = deployments.filter(isNewDeployment)

  private lazy val allDeployments = newDeployments ++ knownDeployments.map(x => ServiceDeployment(x.version, x.productionDate, x.deployers))

  lazy val deploymentsRequiringUpdates = deployments.filter(x => isNewDeployment(x) || isRedeployment(x))

  private lazy val deploymentsSortedByDeploymentdAt = allDeployments.sortBy(_.deploymentdAt.toEpochSecond(ZoneOffset.UTC))

  private lazy val serviceDeploymentIntervals: Seq[(String, Long)] =
    (deploymentsSortedByDeploymentdAt, deploymentsSortedByDeploymentdAt drop 1).zipped.map { case (d1, d2) =>
      (d2.version, daysBetween(d1.deploymentdAt, d2.deploymentdAt))
    }

  def deploymentInterval(version: String): Option[Long] = serviceDeploymentIntervals.find(_._1 == version).map(_._2)

  private def isNewDeployment(deployment: ServiceDeployment): Boolean = !knownDeployments.exists(kr => kr.version == deployment.version)

  private def isRedeployment(deployment: ServiceDeployment): Boolean = knownDeployments.exists(kr => kr.version == deployment.version && kr.deployers != deployment.deployers)
}

object Service {
  def apply(serviceRepositories: (String, Seq[Repository]),
            knownDeployments: Map[String, Seq[ServiceDeployment]],
            knownReleases: Map[String, Seq[Deployment]]): Service =

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

object DeploymentOperation extends Enumeration {
  val Add, Update = Value
}

class DeploymentAndOperation(service: Service, tagDates: Map[String, LocalDateTime]) {

  import uk.gov.hmrc.JavaDateTimeHelper._

  def get: Seq[(DeploymentOperation.Value, Deployment)] = {

    service.deploymentsRequiringUpdates.map { deploymentToUpdate =>
      val tagDate = tagDates.get(deploymentToUpdate.version)

      service.knownDeployments.find(_.version == deploymentToUpdate.version).fold {
        (Add, Deployment(service.serviceName, deploymentToUpdate.version, tagDate, deploymentToUpdate.deploymentdAt, service.deploymentInterval(deploymentToUpdate.version), leadTime(deploymentToUpdate, tagDate), deploymentToUpdate.deployers))
      } { knownDeployment =>
        (Update, knownDeployment.copy(deployers = (knownDeployment.deployers ++ deploymentToUpdate.deployers).distinct))
      }
    }

  }

  def leadTime(nd: ServiceDeployment, tagDate: Option[LocalDateTime]): Option[Long] = {
    tagDate.map(daysBetween(_, nd.deploymentdAt))
  }

}
