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

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import uk.gov.hmrc.servicereleases.deployments.ServiceDeploymentsService
import uk.gov.hmrc.servicereleases.services.ServiceRepositoriesService
import uk.gov.hmrc.servicereleases.tags.TagsService

import scala.concurrent.Future

class DefaultReleasesServiceSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach with DefaultPatienceConfig {

  val servicesService = mock[ServiceRepositoriesService]
  val deploymentsService = mock[ServiceDeploymentsService]
  val tagsService = mock[TagsService]
  val repository = mock[ReleasesRepository]

  val service = new DefaultReleasesService(servicesService, deploymentsService, tagsService, repository)
  val configureMocks: (((String) => ReleasesTestFixture) => Seq[ReleasesTestFixture]) => Map[String, ServiceTestFixture] =
    ServiceTestFixture.configureMocks(servicesService, deploymentsService, tagsService, repository)

  override def beforeEach() = {
    reset(servicesService)
    reset(deploymentsService)
    reset(tagsService)
    reset(repository)

    when(repository.getAll).thenReturn(Future.successful(Map.empty[String, Seq[Release]]))
    when(repository.add(any())).thenReturn(Future.successful(true))
    when(repository.update(any())).thenReturn(Future.successful(true))
  }



  "updateModel" should {

    "Add any new releases for known services to the mongo repository" in {
      val testData = configureMocks(forService => Seq(
        forService("service")
          .repositoryKnowsAbout("1.0.0", "2.0.0")
          .deploymentsKnowsAbout("1.0.0", "2.0.0", "3.0.0")
          .tagsServiceKnowsAbout("1.0.0", "2.0.0", "3.0.0"),
        forService("another")
          .repositoryKnowsAbout("1.0.0")
          .deploymentsKnowsAbout("1.0.0", "1.1.0")
          .tagsServiceKnowsAbout("1.0.0", "1.1.0")))

      service.updateModel().futureValue

      testData("service").verifyReleaseWasAddedToMongo("3.0.0")
      testData("another").verifyReleaseWasAddedToMongo("1.1.0")
    }

    "Add new releases when a service has never been seen before" in {
      val testData = configureMocks(forService => Seq(
        forService("service")
          .repositoryKnowsAbout()
          .deploymentsKnowsAbout("1.0.0")
          .tagsServiceKnowsAbout("1.0.0")))

      service.updateModel().futureValue

      testData("service").verifyReleaseWasAddedToMongo("1.0.0")
    }

    "Add new releases with a blank tag date if only a lightweight tag is available" in {
      val testData = configureMocks(forService => Seq(
        forService("service")
          .repositoryKnowsAbout()
          .deploymentsKnowsAbout("1.0.0")
          .tagsServiceKnowsAbout()))

      val result = service.updateModel().futureValue

      testData("service").verifyReleaseWasAddedToMongoWithBlankCreatedDate("1.0.0")
    }

    "Cope with a scenario where there are no deployments at all for a service" in {
      val testData = configureMocks(forService => Seq(
        forService("service")
          .repositoryKnowsAbout()
          .deploymentsKnowsAbout()
          .tagsServiceKnowsAbout()))

      service.updateModel().futureValue

      verify(tagsService, never).get(any(), any(), any())
      verify(repository, never).add(any())
    }

    "Ignore new releases for services if we fail to fetch the tags" in {
      val testData = configureMocks(forService => Seq(
        forService("service")
          .repositoryKnowsAbout("1.0.0", "2.0.0")
          .deploymentsKnowsAbout("1.0.0", "2.0.0", "3.0.0")
          .tagsServiceKnowsAbout("1.0.0", "2.0.0", "3.0.0"),
        forService("another")
          .repositoryKnowsAbout("1.0.0")
          .deploymentsKnowsAbout("1.0.0", "1.1.0")
          .tagsServiceFailsWith("Error")))

      service.updateModel().futureValue

      testData("service").verifyReleaseWasAddedToMongo("3.0.0")
      testData("another").verifyReleaseWasUpdatedToMongo("1.0.0")


      verify(repository).getAll
      verifyNoMoreInteractions(repository)
    }


    "Add new releases when a service has never been seen before with correct deployment lead time interval" in {
      val testData = configureMocks(forService => Seq(
        forService("service")
          .repositoryKnowsAbout()
          .deploymentsKnowsAbout(Map("1.0.0" -> "02-02-2016"))
          .tagsServiceKnowsAbout(Map("1.0.0" -> "30-01-2016")))
      )

      service.updateModel().futureValue

      testData("service").verifyReleaseWasAddedToMongoWithCorrectLeadTimeInterval("1.0.0" -> Some(3))
    }

    "Add new releases when a service has never been seen before with correct release interval" in {
      val testData = configureMocks(forService => Seq(
        forService("service")
          .repositoryKnowsAbout()
          .deploymentsKnowsAbout(Map("1.0.0" -> "02-02-2016", "2.0.0" -> "04-02-2016"))
      )

      )

      service.updateModel().futureValue

      testData("service").verifyReleaseWasAddedToMongoWithCorrectReleaseInterval("1.0.0" -> None)
      testData("service").verifyReleaseWasAddedToMongoWithCorrectReleaseInterval("2.0.0" -> Some(2))
    }


    "Add new releases when a service has never been seen before and its the first release" in {
      val testData = configureMocks(forService => Seq(
        forService("service")
          .repositoryKnowsAbout()
          .deploymentsKnowsAbout("1.0.0")
          .tagsServiceKnowsAbout("1.0.0")
      ))

      service.updateModel().futureValue

      testData("service").verifyReleaseWasAddedToMongoWithCorrectReleaseInterval("1.0.0" -> None)
    }

    "update missing release interval for existing releases which we already know about and add new releases" in {


      val testData = configureMocks(forService => Seq(
        forService("service")
          .repositoryKnowsAbout(Map("0.1.0" -> "04-02-2016", "1.0.0" -> "06-02-2016"))
          .deploymentsKnowsAbout(Map("0.1.0" -> "04-02-2016", "1.0.0" -> "06-02-2016"))
          .tagsServiceKnowsAbout("0.1.0", "1.0.0")
      ))

      service.updateModel().futureValue

      testData("service").verifyCorrectReleaseIntervalWasUpdatedOnTheRelease("0.1.0" -> None)
      testData("service").verifyCorrectReleaseIntervalWasUpdatedOnTheRelease("1.0.0" -> Some(2))
    }


    "update missing leadtime interval for existing releases which we already know about and add new releases" in {

      val testData = configureMocks(forService => Seq(
        forService("service")
          .repositoryKnowsAboutReleaseWithLeadTime(Map("0.1.0" -> ("04-02-2016", None)))
          .deploymentsKnowsAbout(Map("0.1.0" -> "04-02-2016", "1.0.0" -> "06-02-2016"))
          .tagsServiceKnowsAbout(Map("0.1.0" -> "31-01-2016", "1.0.0" -> "01-02-2016"))
      ))

      service.updateModel().futureValue

      testData("service").verifyCorrectLeadTimeIntervalWasUpdatedOnTheRelease("0.1.0" -> Some(4))
      testData("service").verifyReleaseWasAddedToMongoWithCorrectLeadTimeInterval("1.0.0" -> Some(5))
    }

    "Not do anything if there are no new releases and all existing releases have the lead time interval" in {
      configureMocks(forService => Seq(
        forService("service")
          .repositoryKnowsAboutWithLeadTimeAndInterval(Map("1.0.0" -> (Some(1l), Some(2l))))
          .deploymentsKnowsAbout("1.0.0")
          .tagsServiceKnowsAbout("1.0.0"),
        forService("another")
          .repositoryKnowsAboutWithLeadTimeAndInterval(Map("1.1.1" -> (Some(1l), Some(2l))))
          .deploymentsKnowsAbout("1.1.1")
          .tagsServiceKnowsAbout("1.1.1")))

      service.updateModel().futureValue

      verify(tagsService, never).get(any(), any(), any())
      verify(repository, never).add(any())
      verify(repository, never).update(any())
    }


  }
}
