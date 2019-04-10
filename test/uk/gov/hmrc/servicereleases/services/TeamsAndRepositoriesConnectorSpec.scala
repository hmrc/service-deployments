/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.servicedeployments.services

import com.github.tomakehurst.wiremock.http.RequestMethod.GET
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Seconds, Span}
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.servicedeployments.WireMockSpec

class TeamsAndRepositoriesConnectorSpec
    extends WordSpec
    with Matchers
    with WireMockSpec
    with ScalaFutures
    with GuiceOneAppPerSuite {

  private val stubbedServiceDependenciesConfig =
    Map[String, Any](
      "microservice.services.teams-and-repositories.host" -> host,
      "microservice.services.teams-and-repositories.port" -> port
    )

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(stubbedServiceDependenciesConfig)
      .build()

  val teamsAndrepositoriesConnector: TeamsAndRepositoriesConnector =
    app.injector.instanceOf[TeamsAndRepositoriesConnector]

  "getService" should {

    "return all services with github urls" in {

      implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(4, Seconds), Span(100, Milliseconds))

      givenRequestExpects(
        method = GET,
        url    = s"$endpointMockUrl/api/services",
        willRespondWith = (
          200,
          Some("""|[{
              |			"name": "serviceName",
              |			"githubUrl": {
              |					"name": "github",
              |					"url": "https://someGitHubHost/org1/serviceName"
              |				}
              |		}]
            """.stripMargin))
      )

      teamsAndrepositoriesConnector.getAll().futureValue shouldBe List(
        Service(
          "serviceName",
          GithubUrl("github", "https://someGitHubHost/org1/serviceName")
        )
      )
    }
  }
}
