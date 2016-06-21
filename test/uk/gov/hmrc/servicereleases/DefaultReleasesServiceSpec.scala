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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import uk.gov.hmrc.servicereleases.deployments.{ServiceDeployment, ServiceDeploymentsService}
import uk.gov.hmrc.servicereleases.services.{Repository, ServiceRepositoriesService}
import uk.gov.hmrc.servicereleases.tags.{Tag, TagsService}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class DefaultReleasesServiceSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach with DefaultPatienceConfig {

  val servicesService = mock[ServiceRepositoriesService]
  val deploymentsService = mock[ServiceDeploymentsService]
  val tagsService = mock[TagsService]
  val repository = mock[ReleasesRepository]

  val service = new DefaultReleasesService(servicesService, deploymentsService, tagsService, repository)

  override def beforeEach() = {
    reset(servicesService)
    reset(deploymentsService)
    reset(tagsService)
    reset(repository)
  }

  "updateModel" should {

    "Add any new releases for known services to the mongo repository" in {
      val testData = new ReleasesTestData(servicesService, deploymentsService, tagsService, repository)
        .withKnownVersions("1.0.0", "2.0.0").withNewVersions("3.0.0").forService("service")
        .withKnownVersions("1.0.0").withNewVersions("1.1.0").forService("another")
        .setup()

      val result = service.updateModel().futureValue

      testData.verifyReleasesAddedToRepository()
    }

    "Add new releases when a service has never been seen before" in {
      val testData = new ReleasesTestData(servicesService, deploymentsService, tagsService, repository)
        .withKnownVersions().withNewVersions("1.0.0").forService("service")
        .setup()

      val result = service.updateModel().futureValue

      testData.verifyReleasesAddedToRepository()
    }

    "Cope with a scenario where there are no deployments at all for a service" in {
      val testData = new ReleasesTestData(servicesService, deploymentsService, tagsService, repository)
        .withKnownVersions().withNewVersions().forService("service")
        .withKnownVersions().withNewVersions().forService("another")
        .setup()

      val result = service.updateModel().futureValue

      testData.verifyReleasesAddedToRepository()
    }

    "Ignore new releases for services if we fail to fetch the tags" in {
      val testData = new ReleasesTestData(servicesService, deploymentsService, tagsService, repository)
        .withKnownVersions("1.0.0", "2.0.0").withNewVersions("3.0.0").forService("service")
        .withKnownVersions("1.0.0").withNewVersions("1.1.0").forService("another", withTagFailure = true)
        .setup()

      val result = service.updateModel().futureValue

      testData.verifyReleasesAddedToRepository()
    }

    "Not do anything if there are no new releases" in {
      new ReleasesTestData(servicesService, deploymentsService, tagsService, repository)
        .withKnownVersions("1.0.0", "2.0.0").forService("service")
        .withKnownVersions("1.0.0").forService("another")
        .setup()

      val result = service.updateModel().futureValue

      verify(tagsService, never).get(any(), any(), any())
      verify(repository, never).add(any())
    }
  }
}
