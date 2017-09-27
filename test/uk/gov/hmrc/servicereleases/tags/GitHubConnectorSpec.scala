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

package uk.gov.hmrc.servicedeployments.tags

import java.time.{LocalDateTime, ZoneId}
import java.util.Date

import org.mockito.Mockito._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, TestData, WordSpec}
import org.scalatestplus.play.OneAppPerTest
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.BlockingIOExecutionContext
import uk.gov.hmrc.githubclient.{GhRepoRelease, GithubApiClient}
import uk.gov.hmrc.servicedeployments.ServiceDeploymentsConfig
import uk.gov.hmrc.servicedeployments.services.CatalogueConnector
import uk.gov.hmrc.servicereleases.TestServiceDependenciesConfig

import scala.concurrent.Future


class GitHubConnectorSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures with OneAppPerTest with IntegrationPatience {

  private val stubbedServiceDependenciesConfig = new TestServiceDependenciesConfig()

  private val githubApiClient = mock[GithubApiClient]

  override def newAppForTest(testData: TestData) =
    new GuiceApplicationBuilder()
      .overrides(
        bind[ServiceDeploymentsConfig].toInstance(stubbedServiceDependenciesConfig),
        bind[GithubApiClient].toInstance(githubApiClient)
      ).build()


  lazy val connector = app.injector.instanceOf[GitConnectorOpen]//new GitConnectorOpen(githubApiClient, "")

  "getServiceRepoDeploymentTags" should {

    "get repo deployment tags from git hub deployments" in {
      val now: LocalDateTime = LocalDateTime.now()
      val deployments = List(
        GhRepoRelease(123, "deployments/1.9.0", Date.from(now.atZone(ZoneId.systemDefault()).toInstant)))

      when(githubApiClient.getReleases("OrgA", "repoA")(BlockingIOExecutionContext.executionContext))
        .thenReturn(Future.successful(deployments))

      val tags = connector.get("OrgA", "repoA")

      tags.futureValue shouldBe List(Tag("1.9.0", now))
    }

  }
}
