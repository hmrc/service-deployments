/*
 * Copyright 2018 HM Revenue & Customs
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

import java.time.ZonedDateTime

import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.BlockingIOExecutionContext
import uk.gov.hmrc.gitclient.{GitClient, GitTag}
import uk.gov.hmrc.servicedeployments.ServiceDeploymentsConfig
import uk.gov.hmrc.servicereleases.TestServiceDependenciesConfig

import scala.concurrent.Future

class GitConnectorSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with GuiceOneAppPerSuite
    with IntegrationPatience {
  val mockedGitClient = mock[GitClient]

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      bind[ServiceDeploymentsConfig].toInstance(new TestServiceDependenciesConfig()),
      bind[GitClient].toInstance(mockedGitClient)
    )
    .build()

  "getGitRepoTags" should {

    "return tags form gitClient with normalized tag name (i.e just the numbers)" in {
      val now = ZonedDateTime.now()

      when(mockedGitClient.getGitRepoTags("repoName", "HMRC")(BlockingIOExecutionContext.executionContext))
        .thenReturn(
          Future.successful(
            List(
              GitTag("v1.0.0", Some(now)),
              GitTag("deployment/9.101.0", Some(now)),
              GitTag("someRandomtagName", Some(now)))))
    }

    "try to lookup tag dates from the github deployments if tag date is missing and only return tags which have dates" in {
      val now = ZonedDateTime.now()

      when(mockedGitClient.getGitRepoTags("repoName", "HMRC")(BlockingIOExecutionContext.executionContext))
        .thenReturn(
          Future.successful(
            List(
              GitTag("v1.0.0", None),
              GitTag("deployment/9.101.0", Some(now)),
              GitTag("deployment/9.102.0", None),
              GitTag("someRandomTagName", None))))
    }

  }
}
