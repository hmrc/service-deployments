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

import org.mockito.Matchers._
import org.mockito.Mockito._
import uk.gov.hmrc.servicereleases.deployments.{ServiceDeployment, ServiceDeploymentsService}
import uk.gov.hmrc.servicereleases.services.{Repository, ServiceRepositoriesService}
import uk.gov.hmrc.servicereleases.tags.{Tag, TagsService}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class ReleasesTestData(servicesService: ServiceRepositoriesService,
                       deploymentsService : ServiceDeploymentsService,
                       tagsService: TagsService,
                       repository: ReleasesRepository) {

  private case class ServiceTestData(serviceName: String,
                                     expected: Seq[(String, LocalDateTime, LocalDateTime)],
                                     knownVersions: Seq[String],
                                     unknownVersions: Seq[String])

  private var serviceData: Seq[ServiceTestData] = Seq()

  val now = LocalDateTime.of(2016, 1, 1, 1, 1)
  var expected: Seq[(String, LocalDateTime, LocalDateTime)] = Seq()
  var knownVersions: Seq[String] = List()
  var unknownVersions: Seq[String] = List()

  def withKnownVersions(versions: String*) = {
    knownVersions = versions
    this
  }

  def withNewVersions(versions: String*) = {
    unknownVersions = versions
    this
  }

  def forService(name: String = RandomData.string(8), withTagFailure: Boolean = false) = {

    if (withTagFailure)
      when(tagsService.get("hmrc", name, "github")).thenReturn(
        Future.successful(Failure(new RuntimeException("Ooooarrrg"))))
    else {
      expected = unknownVersions.map { v =>
        val date = RandomData.date()
        (v, date, date.plusHours(1))
      }

      when(tagsService.get("hmrc", name, "github"))
        .thenReturn(Future.successful(
          Success((knownVersions.map { v => Tag(v, now) } ++
            expected.map { case (v, t, p) => Tag(v, t) }).toList)))
    }

    serviceData = serviceData :+ ServiceTestData(name, expected, knownVersions, unknownVersions)

    expected = Seq()
    knownVersions = List()
    unknownVersions = List()

    this
  }

  def setup() = {
    when(servicesService.getAll()).thenReturn(Future.successful(
      serviceData.map { sd => sd.serviceName -> List(Repository("hmrc", "github")) } toMap ))

    when(deploymentsService.getAll()).thenReturn(Future.successful(
      serviceData
        .map { sd => sd.serviceName -> buildDeploymentsData(sd) }
        .filter { case (_, sd) => sd.nonEmpty }
        .toMap
    ))

    when(repository.getAll()).thenReturn(Future.successful(serviceData
      .map { sd => sd.serviceName -> sd.knownVersions.map { v => Release(sd.serviceName, v, now, now) } }
      .filter { case (_, r) => r.nonEmpty }
      .toMap
    ))

    when(repository.add(any())).thenReturn(Future.successful(true))

    this
  }

  def withNoDeployments() =
    when(deploymentsService.getAll()).thenReturn(Future.successful(Map[String, Seq[ServiceDeployment]]()))

  private def buildDeploymentsData(sd: ServiceTestData): Seq[ServiceDeployment] = {
    sd.knownVersions.map { v => ServiceDeployment(v, now) } ++
      sd.expected.map { case (v, t, p) => ServiceDeployment(v, p) }
  }

  def verifyReleasesAddedToRepository() =
    serviceData.foreach { case sd =>
      sd.expected.foreach { case (v, t, p) => verify(repository).add(Release(sd.serviceName, v, t, p)) } }
}
