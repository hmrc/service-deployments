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

import java.io.Serializable
import java.time.{ZoneId, LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter

import org.mockito.Mockito._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.servicereleases.deployments.{ServiceDeployment, ServiceDeploymentsService}
import uk.gov.hmrc.servicereleases.services.{Repository, ServiceRepositoriesService}
import uk.gov.hmrc.servicereleases.tags.{Tag, TagsService}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class ServiceTestFixture(serviceName: String,
                              tags: Try[Seq[Tag]],
                              releases: Seq[Release],
                              deployments: Seq[ServiceDeployment],
                              verifyReleaseWasAddedToMongo: (String) => Unit,
                              verifyReleaseWasAddedToMongoWithBlankCreatedDate: (String) => Unit,
                              verifyReleaseWasAddedToMongoWithCorrectLeadTimeInterval: ((String, Option[Long])) => Unit,
                              verifyCorrectLeadTimeIntervalWasUpdatedOnTheRelease: ((String, Option[Long])) => Unit,
                              verifyReleaseWasAddedToMongoWithCorrectReleaseInterval: ((String, Some[Long])) => Unit)


object ServiceTestFixture {

  val knownReleaseObjectId = BSONObjectID.generate

  def configureMocks(servicesService: ServiceRepositoriesService,
                     deploymentsService: ServiceDeploymentsService,
                     tagsService: TagsService,
                     repository: ReleasesRepository)
                    (fixturesToBuild: ((String) => ReleasesTestFixture) => Seq[ReleasesTestFixture]) = {

    def build(fixture: ReleasesTestFixture) = {
      val defaultVersionDates = (fixture.releaseData.keys ++ fixture.deploymentsData.keys ++ fixture.tagsData.map(_.keys).getOrElse(Seq())).map(_ -> RandomData.date()).toMap

      val tagsDataWithDefaultDates: Try[Map[String, LocalDateTime]] = fixture.tagsData.map { tagsData =>
        tagsData.foldLeft(Map.empty[String, LocalDateTime]) { case (m, (v, dOpt)) =>
          m + (v -> dOpt.getOrElse(defaultVersionDates(v)))
        }
      }

      val deploymentsDataWithDefaultDates = fixture.deploymentsData.foldLeft(Map.empty[String, LocalDateTime]) { case (m, (v, dOpt)) =>
        m + (v -> dOpt.getOrElse(defaultVersionDates(v).plusHours(1)))
      }

      val releasesWithDefaultDates = fixture.releaseData.map {
        case (v, (Some(d), lt)) =>
          Release(fixture.serviceName, v, tagsDataWithDefaultDates.getOrElse(Map()).get(v), d, 1, lt, Some(knownReleaseObjectId))
        case (v, (None, lt)) =>
          Release(fixture.serviceName, v, tagsDataWithDefaultDates.getOrElse(Map()).get(v), deploymentsDataWithDefaultDates(v), 1, lt, Some(knownReleaseObjectId))
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
        verifyReleaseWasAddedToMongoWithCorrectReleaseInterval(fixture, tagsDataWithDefaultDates.getOrElse(Map()), deploymentsDataWithDefaultDates)
      )
    }

    def verifyReleaseWasAddedToMongo(fixture: ReleasesTestFixture, tagDates: Map[String, LocalDateTime], deploymentDates: Map[String, LocalDateTime])(version: String): Unit = {
      verify(repository).add(Release(fixture.serviceName, version, tagDates.get(version), deploymentDates(version), 1, Some(0)))
    }

    def verifyReleaseWasAddedToMongoWithBlankCreatedDate(fixture: ReleasesTestFixture, deploymentDates: Map[String, LocalDateTime])(version: String): Unit = {
      val date = deploymentDates(version)
      verify(repository).add(Release(fixture.serviceName, version, None, date, 1, None))
    }

    def verifyReleaseWasAddedToMongoWithCorrectLeadTimeInterval(fixture: ReleasesTestFixture, tagDates: Map[String, LocalDateTime], deploymentDates: Map[String, LocalDateTime])(versionLeadInterval: (String, Option[Long])): Unit = {

      val (version, leadInterval) = versionLeadInterval
      verify(repository).add(Release(fixture.serviceName, version, tagDates.get(version), deploymentDates(version), 1, leadInterval))
    }

    def verifyReleaseWasAddedToMongoWithCorrectReleaseInterval(fixture: ReleasesTestFixture, tagDates: Map[String, LocalDateTime], deploymentDates: Map[String, LocalDateTime])(versionReleaseInterval: (String, Option[Long])): Unit = {

      val (version, releaseInterval) = versionReleaseInterval
      verify(repository).add(Release(fixture.serviceName, version, tagDates.get(version), deploymentDates(version), 1, Some(0)))
    }


    def verifyCorrectLeadTimeIntervalWasUpdatedOnTheRelease(fixture: ReleasesTestFixture, tagDates: Map[String, LocalDateTime], deploymentDates: Map[String, LocalDateTime])(versionLeadInterval: (String, Option[Long])): Unit = {
      val (version, leadInterval) = versionLeadInterval
      verify(repository).update(Release(fixture.serviceName, version, tagDates.get(version), deploymentDates(version), 1, leadInterval, Some(knownReleaseObjectId)))
    }

    val fixtures = fixturesToBuild(ReleasesTestFixture.apply).map(build)

    when(servicesService.getAll()).thenReturn(Future.successful(
      fixtures.map { sd => sd.serviceName -> List(Repository("hmrc", "github")) } toMap))

    when(deploymentsService.getAll()).thenReturn(Future.successful(
      fixtures
        .map { f => f.serviceName -> f.deployments }
        .filter { case (_, sd) => sd.nonEmpty }
        .toMap
    ))

    when(repository.getAll).thenReturn(Future.successful(
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

object ReleasesTestFixture {
  def apply(name: String): ReleasesTestFixture = new ReleasesTestFixture(name)
}

class ReleasesTestFixture(val serviceName: String = RandomData.string(8)) {

  var releaseData: Map[String, Tuple2[Option[LocalDateTime], Option[Long]]] = Map()
  var deploymentsData: Map[String, Option[LocalDateTime]] = Map()
  var tagsData: Try[Map[String, Option[LocalDateTime]]] = Success(Map())

  def repositoryKnowsAbout(versions: String*) = {

    releaseData = releaseData ++ versions.map((_, (None, Some(0l))))
    this
  }

  def repositoryKnowsAbout(versionAndDeploymentDates: Map[String, String]) = {
    releaseData = releaseData ++ versionAndDeploymentDates.mapValues(x => (Some(toLocalDateTime(x)), Some(0l))).toSeq
    this
  }

  def repositoryKnowsAboutReleaseWithLeadTime(versionAndDeploymentDatesAndLeadTime: Map[String, (String, Option[Long])]) = {
    releaseData = releaseData ++ versionAndDeploymentDatesAndLeadTime.mapValues(x => (Some(toLocalDateTime(x._1)), x._2)).toSeq
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
