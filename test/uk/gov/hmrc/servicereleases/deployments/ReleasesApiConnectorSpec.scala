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

package uk.gov.hmrc.servicedeployments.deployments

import java.time.{LocalDateTime, ZoneOffset}

import com.github.tomakehurst.wiremock.http.RequestMethod
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.OneAppPerTest
import uk.gov.hmrc.servicedeployments.{DefaultPatienceConfig, WireMockSpec}

class DeploymentsApiConnectorSpec extends WordSpec with Matchers with WireMockSpec with ScalaFutures with OneAppPerTest with DefaultPatienceConfig{

  val connector = new DeploymentsApiConnector(endpointMockUrl)

  "Get All" should {

    "get all deployments from the deployments app and return all deployments for the service in production" in {
        val `deployment 11.0.0 date` = LocalDateTime.now().minusDays(5).toEpochSecond(ZoneOffset.UTC)
        val `deployment 8.3.0 date` = LocalDateTime.now().minusMonths(5).toEpochSecond(ZoneOffset.UTC)

        givenRequestExpects(
          method = RequestMethod.GET,
          url = s"$endpointMockUrl/apps",
          willRespondWith = (200,
            Some(
              s"""
              |[
              |    {
              |        "an": "appA",
              |        "env": "prod-something",
              |        "fs": ${`deployment 11.0.0 date`},
              |        "ls": 1450877349,
              |        "ver": "11.0.0"
              |    },
              | {
              |         "an": "appA",
              |         "env": "prod-somethingOther",
              |         "fs": ${`deployment 11.0.0 date`},
              |        "ls": 1450877349,
              |        "ver": "11.0.0"
              |    },
              |    {
              |        "an": "appA",
              |        "env": "qa",
              |        "fs": 1449489675,
              |        "ls": 1450347910,
              |        "ver": "7.3.0"
              |    },
              |    {
              |        "an": "appB",
              |        "env": "qa",
              |        "fs": 1449489675,
              |        "ls": 1450347910,
              |        "ver": "7.3.0"
              |    },
              |    {
              |        "an": "appB",
              |        "env": "prod-something",
              |        "fs": 1449491521,
              |        "ls": 1450879982,
              |        "ver": "5.0.0"
              |    },
              |    {
              |        "an": "appA",
              |        "env": "prod-something",
              |        "deployer_audit": [[ "abc.xyz",${`deployment 8.3.0 date`}]],
              |        "fs": ${`deployment 8.3.0 date`},
              |        "ls": 1450347910,
              |        "ver": "8.3.0"
              |    }
              |
              |]
            """.stripMargin
          )))

          val results = connector.getAll.futureValue
          results.size shouldBe 6
          results.head shouldBe EnvironmentalDeployment(
            "prod-something", "appA", "11.0.0", LocalDateTime.ofEpochSecond(`deployment 11.0.0 date`, 0, ZoneOffset.UTC))

      val expectedDate: LocalDateTime = LocalDateTime.ofEpochSecond(`deployment 8.3.0 date`, 0, ZoneOffset.UTC)
      results.last shouldBe EnvironmentalDeployment(
        "prod-something", "appA", "8.3.0", expectedDate, Seq(Deployer(name ="abc.xyz", deploymentDate = expectedDate )))


    }


  }
}
