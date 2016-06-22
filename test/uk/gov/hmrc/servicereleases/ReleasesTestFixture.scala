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

import org.mockito.Mockito._
import uk.gov.hmrc.servicereleases.deployments.{ServiceDeployment, ServiceDeploymentsService}
import uk.gov.hmrc.servicereleases.services.{Repository, ServiceRepositoriesService}
import uk.gov.hmrc.servicereleases.tags.{Tag, TagsService}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class ServiceTestFixture(serviceName: String,
                              tags: Try[Seq[Tag]],
                              releases: Seq[Release],
                              deployments: Seq[ServiceDeployment],
                              versionDates: Map[String, LocalDateTime],
                              verifyReleaseWasAddedToMongo: (String) => Unit,
                              verifyReleaseWasAddedToMongoWithBlankCreatedDate: (String) => Unit)

object ServiceTestFixture {
  def configureMocks(servicesService: ServiceRepositoriesService,
                     deploymentsService : ServiceDeploymentsService,
                     tagsService: TagsService,
                     repository: ReleasesRepository)
                    (fixturesToBuild: ((String) => ReleasesTestFixture) => Seq[ReleasesTestFixture]) = {

    def build(fixture: ReleasesTestFixture) = {
      val versionDates = (fixture.releaseData ++ fixture.deploymentsData ++ fixture.tagsData.getOrElse(Seq()))
        .distinct.map(_ -> RandomData.date()).toMap

      ServiceTestFixture(
        fixture.serviceName,
        tags = fixture.tagsData.map(_.map { v => Tag(v, versionDates(v)) }),
        releases = fixture.releaseData.map { v => Release(fixture.serviceName, v, Some(versionDates(v)), versionDates(v).plusHours(1)) },
        deployments = fixture.deploymentsData.map { v => ServiceDeployment(v, versionDates(v).plusHours(1)) },
        versionDates,
        verifyReleaseWasAddedToMongo(fixture, versionDates),
        verifyReleaseWasAddedToMongoWithBlankCreatedDate(fixture, versionDates))
    }

    def verifyReleaseWasAddedToMongo(fixture: ReleasesTestFixture, versionDates: Map[String, LocalDateTime])(version: String): Unit = {
      val date = versionDates(version)
      verify(repository).add(Release(fixture.serviceName, version, Some(date), date.plusHours(1)))
    }

    def verifyReleaseWasAddedToMongoWithBlankCreatedDate(fixture: ReleasesTestFixture, versionDates: Map[String, LocalDateTime])(version: String): Unit = {
      val date = versionDates(version)
      verify(repository).add(Release(fixture.serviceName, version, None, date.plusHours(1)))
    }

    val fixtures = fixturesToBuild(ReleasesTestFixture.apply).map(build)

    when(servicesService.getAll()).thenReturn(Future.successful(
      fixtures.map { sd => sd.serviceName -> List(Repository("hmrc", "github")) } toMap ))

    when(deploymentsService.getAll()).thenReturn(Future.successful(
      fixtures
        .map { f => f.serviceName -> f.deployments }
        .filter { case (_, sd) => sd.nonEmpty }
        .toMap
    ))

    when(repository.getAll()).thenReturn(Future.successful(
      fixtures
        .map { f => f.serviceName -> f.releases }
        .filter { case (_, r) => r.nonEmpty }
        .toMap
    ))

    fixtures.foreach { f =>
      when(tagsService.get("hmrc", f.serviceName, "github")).thenReturn(Future.successful(f.tags)) }

    fixtures.map { f => f.serviceName -> f } toMap
  }
}

object ReleasesTestFixture {
  def apply(name: String): ReleasesTestFixture = new ReleasesTestFixture(name)
}

class ReleasesTestFixture(val serviceName: String = RandomData.string(8)) {
  var releaseData: Seq[String] = Seq()
  var deploymentsData: Seq[String] = Seq()
  var tagsData: Try[Seq[String]] = Success(Seq())

  def repositoryKnowsAbout(versions: String*) = {
    releaseData = versions
    this
  }

  def deploymentsKnowsAbout(versions: String*) = {
    deploymentsData = versions
    this
  }

  def tagsServiceKnowsAbout(versions: String*) = {
    tagsData = Success(versions)
    this
  }

  def tagsServiceFailsWith(message: String) = {
    tagsData = Failure(new RuntimeException(message))
    this
  }
}
