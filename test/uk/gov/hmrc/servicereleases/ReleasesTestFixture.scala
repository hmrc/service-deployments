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

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.servicedeployments.deployments.{Deployer, ServiceDeployment, ServiceDeploymentsService}
import uk.gov.hmrc.servicedeployments.services.{Repository, ServiceRepositoriesService}
import uk.gov.hmrc.servicedeployments.tags.{Tag, TagsService}

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class ServiceTestFixture(
  serviceName: String,
  tags: Seq[Tag],
  releases: Seq[Deployment],
  deployments: Seq[ServiceDeployment],
  verifyDeploymentWasAddedToMongo: (String) => Unit,
  verifyDeploymentWasAddedToMongoWithBlankCreatedDate: (String) => Unit,
  verifyDeploymentWasAddedToMongoWithCorrectLeadTimeInterval: ((String, Option[Long])) => Unit,
  verifyCorrectLeadTimeIntervalWasUpdatedOnTheDeployment: ((String, Option[Long])) => Unit,
  verifyDeploymentWasAddedToMongoWithCorrectDeploymentInterval: ((String, Option[Long])) => Unit,
  verifyCorrectDeploymentIntervalWasUpdatedOnTheDeployment: ((String, Option[Long])) => Unit,
  verifyDeploymentWasUpdatedToMongo: (String) => Unit,
  verifyDeploymentWasUpdatedWithCorrectDeployers: ((String, Seq[String])) => Unit,
  verifyDeploymentWasAddedWithCorrectDeployers: ((String, Seq[String])) => Unit)

object ServiceTestFixture {

  val knownReleaseObjectId = BSONObjectID.generate

  def configureMocks(
    servicesService: ServiceRepositoriesService,
    deploymentsService: ServiceDeploymentsService,
    tagsService: TagsService,
    repository: DeploymentsRepository)(
    fixturesToBuild: ((String) => DeploymentsTestFixture) => Seq[DeploymentsTestFixture]) = {

    def build(fixture: DeploymentsTestFixture) = {
      val defaultVersionDates =
        (fixture.releaseData.keys ++ fixture.deploymentsData.keys ++ fixture.tagsData.keys)
          .map(_ -> RandomData.date())
          .toMap

      val tagsDataWithDefaultDates: Map[String, LocalDateTime] = fixture.tagsData
        .foldLeft(Map.empty[String, LocalDateTime]) {
          case (m, (v, dOpt)) =>
            m + (v -> dOpt.getOrElse(defaultVersionDates(v)))
        }

      val deploymentsDataWithDefaultDates = fixture.deploymentsData.foldLeft(List.empty[ServiceDeployment]) {
        case (ls, (v, dOpt)) =>
          ls :+ ServiceDeployment(v, dOpt.getOrElse(defaultVersionDates(v).plusHours(1)))

      } ++ fixture.deploymentsDataWithUser.foldLeft(List.empty[ServiceDeployment]) {
        case (ls, (v, user)) =>
          val deploymentAt: LocalDateTime = defaultVersionDates(v).plusHours(1)
          ls :+ ServiceDeployment(v, deploymentAt, Seq(Deployer(user, deploymentAt)))
      }

      val releasesWithDefaultDates = fixture.releaseData.map {
        case (v, (Some(d), lt, ri)) =>
          Deployment(
            fixture.serviceName,
            v,
            tagsDataWithDefaultDates.get(v),
            d,
            ri,
            lt,
            Nil,
            Some(knownReleaseObjectId))
        case (v, (None, lt, ri)) =>
          Deployment(
            fixture.serviceName,
            v,
            tagsDataWithDefaultDates.get(v),
            deploymentsDataWithDefaultDates.find(_.version == v).get.deploymentdAt,
            ri,
            lt,
            Nil,
            Some(knownReleaseObjectId)
          )
      }.toSeq

      ServiceTestFixture(
        fixture.serviceName,
        tags        = tagsDataWithDefaultDates.map { case (v, d) => Tag(v, d) }.toSeq,
        releases    = releasesWithDefaultDates,
        deployments = deploymentsDataWithDefaultDates,
        verifyReleaseWasAddedToMongo(fixture, tagsDataWithDefaultDates),
        verifyReleaseWasAddedToMongoWithBlankCreatedDate(fixture),
        verifyReleaseWasAddedToMongoWithCorrectLeadTimeInterval(fixture, tagsDataWithDefaultDates),
        verifyCorrectLeadTimeIntervalWasUpdatedOnTheRelease(fixture, tagsDataWithDefaultDates),
        verifyReleaseWasAddedToMongoWithCorrectReleaseInterval(fixture, tagsDataWithDefaultDates),
        verifyCorrectReleaseIntervalWasUpdatedOnTheRelease(fixture, tagsDataWithDefaultDates),
        verifyReleaseWasUpdatedToMongo(fixture, tagsDataWithDefaultDates),
        verifyDeploymentWasUpdatedWithCorrectDeployers(fixture),
        verifyDeploymentWasAddedWithCorrectDeployers(fixture)
      )
    }

    def verifyDeploymentWasUpdatedWithCorrectDeployers(fixture: DeploymentsTestFixture)(
      versionAndDeployer: (String, Seq[String])): Unit = {

      val (version, deployers) = versionAndDeployer
      val releaseCaptur        = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository, atLeastOnce()).update(releaseCaptur.capture())

      assert(
        releaseCaptur.getAllValues.toList.exists(x => x.version == version && x.deployers.map(_.name) == deployers),
        s"release : $version was not Added to mongo")
    }

    def verifyDeploymentWasAddedWithCorrectDeployers(fixture: DeploymentsTestFixture)(
      versionAndDeployer: (String, Seq[String])): Unit = {
      val (version, deployers) = versionAndDeployer
      val releaseCaptur        = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository, atLeastOnce()).add(releaseCaptur.capture())

      assert(
        releaseCaptur.getAllValues.toList.exists(x => x.version == version && x.deployers.map(_.name) == deployers),
        s"release : $version was not updated to mongo")

    }

    def verifyReleaseWasAddedToMongo(fixture: DeploymentsTestFixture, tagDates: Map[String, LocalDateTime])(
      version: String): Unit = {

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository, atLeastOnce()).add(releaseCaptur.capture())

      assert(
        releaseCaptur.getAllValues.toList.exists(_.version == version),
        s"release : $version was not added to mongo")
    }

    def verifyReleaseWasUpdatedToMongo(fixture: DeploymentsTestFixture, tagDates: Map[String, LocalDateTime])(
      version: String): Unit = {

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository, atLeastOnce()).update(releaseCaptur.capture())

      assert(
        releaseCaptur.getAllValues.toList.exists(_.version == version),
        s"release : $version was not updated to mongo")
    }

    def verifyReleaseWasAddedToMongoWithBlankCreatedDate(fixture: DeploymentsTestFixture)(version: String): Unit = {

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository).add(releaseCaptur.capture())

      assert(
        releaseCaptur.getAllValues.toList.find(_.version == version).exists(_.creationDate == None),
        s"release : $version was not added to mongo With blank creation date"
      )
    }

    def verifyReleaseWasAddedToMongoWithCorrectLeadTimeInterval(
      fixture: DeploymentsTestFixture,
      tagDates: Map[String, LocalDateTime])(versionLeadInterval: (String, Option[Long])): Unit = {

      val (version, leadInterval) = versionLeadInterval

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository).add(releaseCaptur.capture())

      assert(
        releaseCaptur.getAllValues.toList.find(_.version == version).exists(_.leadTime == leadInterval),
        s"release : $version was not added to mongo With leadTime : $leadInterval"
      )
    }

    def verifyReleaseWasAddedToMongoWithCorrectReleaseInterval(
      fixture: DeploymentsTestFixture,
      tagDates: Map[String, LocalDateTime])(versionReleaseInterval: (String, Option[Long])): Unit = {

      val (version, releaseInterval) = versionReleaseInterval

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository, atLeastOnce()).add(releaseCaptur.capture())

      assert(
        releaseCaptur.getAllValues.toList.find(_.version == version).exists(_.interval == releaseInterval),
        s"release : $version was not added to mongo With releaseInterval : $releaseInterval"
      )
    }

    def verifyCorrectLeadTimeIntervalWasUpdatedOnTheRelease(
      fixture: DeploymentsTestFixture,
      tagDates: Map[String, LocalDateTime])(versionLeadInterval: (String, Option[Long])): Unit = {
      val (version, leadInterval) = versionLeadInterval

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository, atLeastOnce()).update(releaseCaptur.capture())

      val release: Option[Deployment] = releaseCaptur.getAllValues.toList.find(_.version == version)
      assert(
        release.exists(_.leadTime == leadInterval),
        s"release : $version was not updated to mongo With leadTimeInterval : $leadInterval")
      assert(
        release.exists(_._id == Some(knownReleaseObjectId)),
        s"release : $version was not updated to mongo With _id : $knownReleaseObjectId")

    }

    def verifyCorrectReleaseIntervalWasUpdatedOnTheRelease(
      fixture: DeploymentsTestFixture,
      tagDates: Map[String, LocalDateTime])(versionReleaseInterval: (String, Option[Long])): Unit = {
      val (version, releaseInterval) = versionReleaseInterval

      val releaseCaptur = ArgumentCaptor.forClass(classOf[Deployment])

      verify(repository, atLeastOnce()).update(releaseCaptur.capture())

      val release: Option[Deployment] = releaseCaptur.getAllValues.toList.find(_.version == version)
      assert(
        release.exists(_.interval == releaseInterval),
        s"release : $version was not updated to mongo With releaseInterval : $releaseInterval")
      assert(
        release.exists(_._id.contains(knownReleaseObjectId)),
        s"release : $version was not updated to mongo With _id : $knownReleaseObjectId")
    }

    val fixtures = fixturesToBuild(DeploymentsTestFixture.apply).map(build)

    when(servicesService.getAll()).thenReturn(Future.successful(fixtures.map { sd =>
      sd.serviceName -> List(Repository("hmrc"))
    } toMap))

    when(deploymentsService.getAll()).thenReturn(
      Future.successful(
        fixtures
          .map { f =>
            f.serviceName -> f.deployments
          }
          .filter { case (_, sd) => sd.nonEmpty }
          .toMap
      ))

    when(repository.allServicedeployments).thenReturn(
      Future.successful(
        fixtures
          .map { f =>
            f.serviceName -> f.releases
          }
          .filter { case (_, r) => r.nonEmpty }
          .toMap
      ))

    fixtures.foreach { f =>
      when(tagsService.get("hmrc", f.serviceName)).thenReturn(Future.successful(f.tags))
    }

    fixtures.map { f =>
      f.serviceName -> f
    } toMap
  }

}

object DeploymentsTestFixture {
  def apply(name: String): DeploymentsTestFixture = new DeploymentsTestFixture(name)
}

class DeploymentsTestFixture(val serviceName: String = RandomData.string(8)) {

  var releaseData: Map[String, (Option[LocalDateTime], Option[Long], Option[Long])] = Map()
  var deploymentsData: Map[String, Option[LocalDateTime]]                                 = Map()
  var deploymentsDataWithUser: Map[String, String]                                        = Map()
  var tagsData: Map[String, Option[LocalDateTime]]                                  = Map.empty

  def repositoryKnowsAbout(versions: String*) = {

    releaseData = releaseData ++ versions.map((_, (None, None, None)))
    this
  }

  def repositoryKnowsAboutWithLeadTimeAndInterval(
    versionLeadTimeAndInterval: Map[String, (Option[Long], Option[Long])]) = {

    releaseData = releaseData ++ versionLeadTimeAndInterval.mapValues {
      case (leadTime, releaseInterval) => (None, leadTime, releaseInterval)
    }.toSeq
    this
  }

  def repositoryKnowsAbout(versionAndDeploymentDates: Map[String, String]) = {
    releaseData = releaseData ++ versionAndDeploymentDates.mapValues(x => (Some(toLocalDateTime(x)), None, None)).toSeq
    this
  }

  def repositoryKnowsAboutDeploymentWithLeadTime(
    versionAndDeploymentDatesAndLeadTime: Map[String, (String, Option[Long])]) = {
    releaseData = releaseData ++ versionAndDeploymentDatesAndLeadTime
      .mapValues(x => (Some(toLocalDateTime(x._1)), x._2, Some(0l)))
      .toSeq
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

  def deploymentsKnowsAboutWithDeployer(versionAndDeployer: Map[String, String]) = {
    require(versionAndDeployer.values.forall(_.nonEmpty), "date cannot be empty")
    deploymentsDataWithUser = deploymentsDataWithUser ++ versionAndDeployer
    this
  }

  def tagsServiceKnowsAbout(versions: String*) = {
    tagsData = tagsData ++ versions.map((_, None))
    this
  }

  def tagsServiceKnowsAbout(versionAndTagDates: Map[String, String]) = {
    tagsData = tagsData ++ versionAndTagDates.mapValues(x => Some(toLocalDateTime(x))).toSeq
    this
  }

  def tagsServiceFailsWith(message: String) = {
    tagsData = Map.empty
    //tagsData = new RuntimeException(message)
    this
  }

  private def toLocalDateTime(dateS: String) =
    LocalDate.parse(dateS, DateTimeFormatter.ofPattern("dd-MM-yyyy")).atStartOfDay()
}
