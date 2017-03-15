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

package uk.gov.hmrc.servicedeployments.services

import com.github.tomakehurst.wiremock.http.RequestMethod.GET
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Seconds, Span}
import org.scalatest.{Matchers, TestData, WordSpec}
import org.scalatestplus.play.OneAppPerTest
import play.api.Application
import play.api.inject.Module
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeApplication
import play.api.test.Helpers._
import uk.gov.hmrc.servicedeployments.WireMockSpec

class CatalogueConnectorSpec extends WordSpec with Matchers with WireMockSpec with ScalaFutures with OneAppPerTest{
  val catalogueClient = new CatalogueConnector(endpointMockUrl)

  implicit override def newAppForTest(testData: TestData): Application =
    new GuiceApplicationBuilder().disable(classOf[com.kenshoo.play.metrics.PlayModule]).build()

  "getService" should {

    "return all services with github urls" in running(FakeApplication()) {

      implicit val patienceConfig = PatienceConfig(Span(4, Seconds), Span(100, Milliseconds))

      givenRequestExpects(
        method = GET,
        url = s"$endpointMockUrl/services",
        willRespondWith = (200,
          Some(
            """|[{
              |			"name": "serviceName",
              |			"githubUrls": [
              |				{
              |					"name": "github",
              |					"url": "https://someGitHubHost/org1/serviceName"
              |				},
              |    {
              |					"name": "github-open",
              |					"url": "https://someOtherGitHubHost/org2/serviceName"
              |				}
              |			]
              |		}]
            """.stripMargin)))

      catalogueClient.getAll().futureValue shouldBe List(
        Service("serviceName", List(
          GithubUrl("github", "https://someGitHubHost/org1/serviceName"),
          GithubUrl("github-open", "https://someOtherGitHubHost/org2/serviceName"))))
    }
  }
}
