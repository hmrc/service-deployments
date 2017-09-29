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

import java.time.{LocalDateTime, ZoneId, ZoneOffset}
import java.util.Date

import org.mockito.Mockito._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, TestData, WordSpec}
import org.scalatestplus.play.OneAppPerTest
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.BlockingIOExecutionContext
import uk.gov.hmrc.gitclient.{GitClient, GitTag}
import uk.gov.hmrc.githubclient.{GhRepoRelease, GithubApiClient}
import uk.gov.hmrc.servicedeployments.{GithubApiClientEnterprise, GithubApiClientOpen, ServiceDeploymentsConfig}
import uk.gov.hmrc.servicedeployments.services.CatalogueConnector
import uk.gov.hmrc.servicereleases.TestServiceDependenciesConfig

import scala.concurrent.Future


class GitHubConnectorSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures with OneAppPerTest with IntegrationPatience {

  private val stubbedServiceDependenciesConfig = new TestServiceDependenciesConfig()

  private val githubApiClientEnterprise = mock[GithubApiClientEnterprise]
  private val githubApiClientOpen = mock[GithubApiClientOpen]
  private val mockedGitClient = mock[GitClient]

  override def newAppForTest(testData: TestData) =
    new GuiceApplicationBuilder()
      .overrides(
        bind[ServiceDeploymentsConfig].toInstance(stubbedServiceDependenciesConfig),
        bind[GitClient].toInstance(mockedGitClient),
        bind[GithubApiClientEnterprise].toInstance(githubApiClientEnterprise),
        bind[GithubApiClientOpen].toInstance(githubApiClientOpen)
      ).build()


  lazy val githubOpenConnector = app.injector.instanceOf[GitConnectorOpen]
  lazy val githubEnterpriseConnector = app.injector.instanceOf[GitConnectorEnterprise]

  "getServiceRepoDeploymentTags" should {

    "get repo deployment tags from github open deployments" in {
      val now: LocalDateTime = LocalDateTime.now()
      val deployments = List(
        GhRepoRelease(123, "deployments/1.9.0", Date.from(now.atZone(ZoneId.systemDefault()).toInstant)))

      when(githubApiClientOpen.getReleases("OrgA", "repoA")(BlockingIOExecutionContext.executionContext))
        .thenReturn(Future.successful(deployments))

      val tags = githubOpenConnector.get("OrgA", "repoA")

      tags.futureValue shouldBe List(Tag("1.9.0", now))
    }
    
    "get repo deployment tags from github enterprise deployments" in {
      val now: LocalDateTime = LocalDateTime.now()
      val deployments = List(
        GhRepoRelease(123, "deployments/1.9.0", Date.from(now.atZone(ZoneId.systemDefault()).toInstant)))

      when(githubApiClientEnterprise.getReleases("OrgA", "repoA")(BlockingIOExecutionContext.executionContext))
        .thenReturn(Future.successful(deployments))

      when(mockedGitClient.getGitRepoTags("repoA", "OrgA")(BlockingIOExecutionContext.executionContext))
        .thenReturn(Future.successful(List(GitTag("1.9.0", Some(now.atZone(ZoneOffset.UTC))))))


      val tags = githubEnterpriseConnector.get("OrgA", "repoA")

      tags.futureValue shouldBe List(Tag("1.9.0", now))
    }

  }
}
