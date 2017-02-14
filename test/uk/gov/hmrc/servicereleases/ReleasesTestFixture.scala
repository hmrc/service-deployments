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

import java.io.Serializable
import java.time.{ZoneId, LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter

import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.servicedeployments.deployments.{ServiceDeployment, ServiceDeploymentsService}
import uk.gov.hmrc.servicedeployments.services.{Repository, ServiceRepositoriesService}
import uk.gov.hmrc.servicedeployments.tags.{Tag, TagsService}
import scala.collection.JavaConversions._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class ServiceTestFixture(serviceName: String,
                              tags: Try[Seq[Tag]],
                              releases: Seq[Deployment],
                              deployments: Seq[ServiceDeployment],
                              verifyDeploymentWasAddedToMongo: (String) => Unit,
                              verifyDeploymentWasAddedToMongoWithBlankCreatedDate: (String) => Unit,
                              verifyDeploymentWasAddedToMongoWithCorrectLeadTimeInterval: ((String, Option[Long])) => Unit,
                              verifyCorrectLeadTimeIntervalWasUpdatedOnTheDeployment: ((String, Option[Long])) => Unit,
                              verifyDeploymentWasAddedToMongoWithCorrectDeploymentInterval: ((String, Option[Long])) => Unit,
                              verifyCorrectDeploymentIntervalWasUpdatedOnTheDeployment: ((String, Option[Long])) => Unit,
                              verifyDeploymentWasUpdatedToMongo: (String) => Unit)


object ServiceTestFixture {

  val knownReleaseObjectId = BSONObjectID.generate

  def configureMocks(servicesService: ServiceRepositoriesService,
                     deploymentsService: ServiceDeploymentsService,
                     tagsService: TagsService,
                     repository: DeploymentsRepository)
                    (fixturesToBuild: ((String) => DeploymentsTestFixture) => Seq[DeploymentsTestFixture]) = {

    def build(fixture: DeploymentsTestFixture) = {
      val defaultVersionDates = (fixture.releaseData.keys ++ fixture.deploymentsData.keys ++ fixture.tagsData.map(_.keys).getOrElse(Seq())).map(_ -> RandomData.date()).toMap

      val tagsDataWithDefaultDates: Try[Map[String, LocalDateTime]] =  fixture.tagsData.map { tagsData =>
        tagsData.foldLeft(Map.empty[String, LocalDateTime]) { case (m, (v, dOpt)) =>
          m + (v -> dOpt.getOrElse(defaultVersionDates(v)))
        }
      }

      val deploymentsDataWithDefaultDates = fixture.deploymentsData.foldLeft(Map.empty[String, LocalDateTime]) { case (m, (v, dOpt)) =>
        m + (v -> dOpt.getOrElse(defaultVersionDates(v).plusHours(1)))
      }

      val releasesWithDefaultDates = fixture.releaseData.map {
        case (v, (Some(d), lt, ri)) =>
          Deployment(fixture.serviceName, v, tagsDataWithDefaultDates.getOrElse(Map()).get(v), d, ri, lt, Some(knownReleaseObjectId))
        case (v, (None, lt, ri)) =>
          Deployment(fixture.serviceName, v, tagsDataWithDefaultDates.getOrElse(Map()).get(v), deploymentsDataWithDefaultDates(v), ri, lt, Some(knownReleaseObjectId))
      }.toSeq


      ServiceTestFixture(
        fixture.serviceName,
        tags = tagsDataWithDefaultDates.map(x => x.map { case (v, d) => Tag(v, d) }.toSeq),
        releases = releasesWithDefaultDates,
        deployments = deploymentsDataWithDefaultDates.map { case (v, d) => ServiceDeployment(v, d) }.toSeq,
        verifyReleaseWasAddedToMongo(fixture, tagsDataWithDefaultDates.getOrElse(Map()), deploymentsDataWithDefaultDates),
        verifyReleaseWasAddedToMongoWithBlankCreatedDate(fixture, deploymentsDataWithDefaultDates),
        verifyReleaseWasAddedToMongoWithCorrectLeadTimeInterval(fixture, tagsDataWithDefaultDates.getOrElse(Map()), deploymentsDataWithDefaultDates),
        verifyCorrectLeadTimeIntervalWasUpdatedOnTheRelease(fixture, tagsDataWithDefaultDates.getOrElse(Map()), deploymentsDataWithDefaultDates),
        verifyReleaseWasAddedToMongoWithCorrectReleaseInterval(fixture, tagsDataWithDefaultDates.getOrElse(Map()), deploymentsDataWithDefaultDates),
        verifyCorrectReleaseIntervalWasUpdatedOnTheRelease(fixture, tagsDataWithDefaultDates.getOrElse(Map()), deploymentsDataWithDefaultDates),
        verifyReleaseWasUpdatedToMongo(fixture, tagsDataWithDefaultDates.getOrElse(Map()), deploymentsDataWithDefaultDates)
      )
    }

    def verifyReleaseWasAddedToMongo(fixture: DeploymentsTestFixture, tagDates: Map[String, LocalDateTime], deploymentDates: Map[String, LocalDateTime])(version: String): Unit = {

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository, atLeastOnce()).add(releaseCaptur.capture())

      assert(releaseCaptur.getAllValues.toList.exists(_.version == version), s"release : $version was not added to mongo")
    }


    def verifyReleaseWasUpdatedToMongo(fixture: DeploymentsTestFixture, tagDates: Map[String, LocalDateTime], deploymentDates: Map[String, LocalDateTime])(version: String): Unit = {

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository, atLeastOnce()).update(releaseCaptur.capture())

      assert(releaseCaptur.getAllValues.toList.exists(_.version == version), s"release : $version was not updated to mongo")
    }


    def verifyReleaseWasAddedToMongoWithBlankCreatedDate(fixture: DeploymentsTestFixture, deploymentDates: Map[String, LocalDateTime])(version: String): Unit = {

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])


      verify(repository).add(releaseCaptur.capture())

      assert(releaseCaptur.getAllValues.toList.find(_.version == version).exists(_.creationDate == None), s"release : $version was not added to mongo With blank creation date")
    }

    def verifyReleaseWasAddedToMongoWithCorrectLeadTimeInterval(fixture: DeploymentsTestFixture, tagDates: Map[String, LocalDateTime], deploymentDates: Map[String, LocalDateTime])(versionLeadInterval: (String, Option[Long])): Unit = {

      val (version, leadInterval) = versionLeadInterval

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository).add(releaseCaptur.capture())

      assert(releaseCaptur.getAllValues.toList.find(_.version == version).exists(_.leadTime == leadInterval), s"release : $version was not added to mongo With leadTime : $leadInterval")
    }

    def verifyReleaseWasAddedToMongoWithCorrectReleaseInterval(fixture: DeploymentsTestFixture, tagDates: Map[String, LocalDateTime], deploymentDates: Map[String, LocalDateTime])(versionReleaseInterval: (String, Option[Long])): Unit = {

      val (version, releaseInterval) = versionReleaseInterval

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository, atLeastOnce()).add(releaseCaptur.capture())

      assert(releaseCaptur.getAllValues.toList.find(_.version == version).exists(_.interval == releaseInterval), s"release : $version was not added to mongo With releaseInterval : $releaseInterval")
    }


    def verifyCorrectLeadTimeIntervalWasUpdatedOnTheRelease(fixture: DeploymentsTestFixture, tagDates: Map[String, LocalDateTime], deploymentDates: Map[String, LocalDateTime])(versionLeadInterval: (String, Option[Long])): Unit = {
      val (version, leadInterval) = versionLeadInterval

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository, atLeastOnce()).update(releaseCaptur.capture())

      val release: Option[Deployment] = releaseCaptur.getAllValues.toList.find(_.version == version)
      assert(release.exists(_.leadTime == leadInterval), s"release : $version was not updated to mongo With leadTimeInterval : $leadInterval")
      assert(release.exists(_._id == Some(knownReleaseObjectId)), s"release : $version was not updated to mongo With _id : $knownReleaseObjectId")

    }

    def verifyCorrectReleaseIntervalWasUpdatedOnTheRelease(fixture: DeploymentsTestFixture, tagDates: Map[String, LocalDateTime], deploymentDates: Map[String, LocalDateTime])(versionReleaseInterval: (String, Option[Long])): Unit = {
      val (version, releaseInterval) = versionReleaseInterval

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository, atLeastOnce()).update(releaseCaptur.capture())

      val release: Option[Deployment] = releaseCaptur.getAllValues.toList.find(_.version == version)
      assert(release.exists(_.interval == releaseInterval), s"release : $version was not updated to mongo With releaseInterval : $releaseInterval")
      assert(release.exists(_._id == Some(knownReleaseObjectId)), s"release : $version was not updated to mongo With _id : $knownReleaseObjectId")
    }

    val fixtures = fixturesToBuild(DeploymentsTestFixture.apply).map(build)

    when(servicesService.getAll()).thenReturn(Future.successful(
      fixtures.map { sd => sd.serviceName -> List(Repository("hmrc", "github")) } toMap))

    when(deploymentsService.getAll()).thenReturn(Future.successful(
      fixtures
        .map { f => f.serviceName -> f.deployments }
        .filter { case (_, sd) => sd.nonEmpty }
        .toMap
    ))

    when(repository.allServicedeployments).thenReturn(Future.successful(
      fixtures
        .map { f => f.serviceName -> f.releases }
        .filter { case (_, r) => r.nonEmpty }
        .toMap
    ))

    fixtures.foreach { f =>
      when(tagsService.get("hmrc", f.serviceName, "github")).thenReturn(Future.successful(f.tags))
    }

    fixtures.map { f => f.serviceName -> f } toMap
  }

}

object DeploymentsTestFixture {
  def apply(name: String): DeploymentsTestFixture = new DeploymentsTestFixture(name)
}

class DeploymentsTestFixture(val serviceName: String = RandomData.string(8)) {

  var releaseData: Map[String, Tuple3[Option[LocalDateTime], Option[Long], Option[Long]]] = Map()
  var deploymentsData: Map[String, Option[LocalDateTime]] = Map()
  var tagsData: Try[Map[String, Option[LocalDateTime]]] = Success(Map())

  def repositoryKnowsAbout(versions: String*) = {

    releaseData = releaseData ++ versions.map((_, (None, None, None)))
    this
  }

  def repositoryKnowsAboutWithLeadTimeAndInterval(versionLeadTimeAndInterval: Map[String, (Option[Long], Option[Long])]) = {

    releaseData = releaseData ++ versionLeadTimeAndInterval.mapValues{case (leadTime, releaseInterval) => (None, leadTime, releaseInterval)}.toSeq
    this
  }

  def repositoryKnowsAbout(versionAndDeploymentDates: Map[String, String]) = {
    releaseData = releaseData ++ versionAndDeploymentDates.mapValues(x => (Some(toLocalDateTime(x)), None, None)).toSeq
    this
  }

  def repositoryKnowsAboutDeploymentWithLeadTime(versionAndDeploymentDatesAndLeadTime: Map[String, (String, Option[Long])]) = {
    releaseData = releaseData ++ versionAndDeploymentDatesAndLeadTime.mapValues(x => (Some(toLocalDateTime(x._1)), x._2, Some(0l))).toSeq
    this
  }


  def deploymentsKnowsAbout(versions: String*) = {
    deploymentsData = deploymentsData ++ versions.map((_, None))
    this
  }

  def deploymentsKnowsAbout(versionAndDeploymentDates: Map[String, String]) = {
    require(versionAndDeploymentDates.values.forall(_.nonEmpty), "date cannot be empty")
    deploymentsData = deploymentsData ++ versionAndDeploymentDates.mapValues(x => Some(toLocalDateTime(x))).toSeq
    this
  }

  def tagsServiceKnowsAbout(versions: String*) = {
    tagsData = tagsData.map(_ ++ versions.map((_, None)))
    this
  }

  def tagsServiceKnowsAbout(versionAndTagDates: Map[String, String]) = {

    tagsData = tagsData.map(_ ++ versionAndTagDates.mapValues(x => Some(toLocalDateTime(x))).toSeq)
    this
  }

  def tagsServiceFailsWith(message: String) = {
    tagsData = Failure(new RuntimeException(message))
    this
  }

  private def toLocalDateTime(dateS: String) = LocalDate.parse(dateS, DateTimeFormatter.ofPattern("dd-MM-yyyy")).atStartOfDay()
}
