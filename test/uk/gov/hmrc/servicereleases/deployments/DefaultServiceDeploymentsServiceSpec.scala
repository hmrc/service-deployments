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

package uk.gov.hmrc.servicereleases.deployments

import java.time.LocalDateTime

import org.mockito.Mockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Future

class DefaultServiceDeploymentsServiceSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures {
  
  val dataSource = mock[DeploymentsDataSource]

  val service = new DefaultServiceDeploymentsService(dataSource)
  val now = LocalDateTime.now()

  "Get all" should {

    "give all releases for a given service in production" in {
      Mockito.when(dataSource.getAll).thenReturn(Future.successful(
        List(
          Deployment("production","some-serviceName","1.0",now),
          Deployment("prod","some-serviceName","2.0",now),
          Deployment("production","some-other-ServiceName","1.0",now.minusDays(2)))
        ))

      val result = service.getAll().futureValue
      result("some-serviceName") shouldBe Seq(ServiceDeployment("1.0",now), ServiceDeployment("2.0",now))
      result("some-other-ServiceName") shouldBe Seq(ServiceDeployment("1.0", now.minusDays(2)))
    }

    "remove re releases and take the release with earliest date" in {
      val twoDaysEarlier = now.minusDays(2)
      val aHourEarlier = now.minusHours(1)
      Mockito.when(dataSource.getAll).thenReturn(Future.successful(
        List(
          Deployment("production","some-serviceName","1.0",now),
          Deployment("prod","some-serviceName","1.0",aHourEarlier),
          Deployment("production","some-serviceName","1.0", twoDaysEarlier),
          Deployment("production","some-other-serviceName","1.0", twoDaysEarlier)
        )
      ))

      val result = service.getAll().futureValue
      result("some-serviceName") shouldBe Seq(ServiceDeployment("1.0",twoDaysEarlier))
      result("some-other-serviceName") shouldBe Seq(ServiceDeployment("1.0",twoDaysEarlier))
    }

    "releases should be sorted by date" in {
      val twoDaysEarlier = now.minusDays(2)
      val aHourEarlier = now.minusHours(1)
      Mockito.when(dataSource.getAll).thenReturn(Future.successful(
        List(
          Deployment("production","some-serviceName","3.0",now),
          Deployment("prod","some-serviceName","2.0",aHourEarlier),
          Deployment("production","some-serviceName","1.0", twoDaysEarlier)
        )
      ))

      service.getAll().futureValue shouldBe Map(
        "some-serviceName" -> Seq(
          ServiceDeployment("1.0", twoDaysEarlier),
          ServiceDeployment("2.0", aHourEarlier),
          ServiceDeployment("3.0", now)))
    }
  }
}
