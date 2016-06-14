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

package uk.gov.hmrc.servicereleases.services

import com.github.tomakehurst.wiremock.http.RequestMethod.GET
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import play.api.test.FakeApplication
import play.api.test.Helpers._
import uk.gov.hmrc.servicereleases.WireMockSpec

class CatalogueConnectorSpec extends WordSpec with Matchers with WireMockSpec with ScalaFutures {
  val catalogueClient = new CatalogueConnector(endpointMockUrl)

  "getService" should {

    "return all services with github urls" in running(FakeApplication()) {

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


