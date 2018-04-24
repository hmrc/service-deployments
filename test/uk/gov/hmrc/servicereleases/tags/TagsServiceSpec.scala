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

import java.time.LocalDateTime

import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, TestData, WordSpec}
import org.scalatestplus.play.OneAppPerTest
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.servicedeployments.ServiceDeploymentsConfig
import uk.gov.hmrc.servicereleases.TestServiceDependenciesConfig

import scala.concurrent.Future
import scala.util.{Failure, Success}

class TagsServiceSpec extends WordSpec with Matchers with MockitoSugar with OneAppPerTest with ScalaFutures {

  implicit override def newAppForTest(testData: TestData): Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[ServiceDeploymentsConfig].toInstance(new TestServiceDependenciesConfig())
      )
      .build()

  trait SetUp {
    val gitOpenTagDataSource = mock[GitConnectorOpen]
    val compositeTagsSource  = new TagsService(gitOpenTagDataSource)
  }

  "getAll" should {

    val repoName = "service"
    val org      = "org"

    "use enterprise data source if RepoType is Enterprise" in new SetUp {
      val repoType            = "github-enterprise"
      val repoTags: List[Tag] = List(Tag("E", LocalDateTime.now()))
      compositeTagsSource.get(org, repoName, repoType).futureValue shouldBe Success(repoTags)
      verifyZeroInteractions(gitOpenTagDataSource)
    }

    "use open data source if RepoType is Open" in new SetUp {
      val repoType            = "github-com"
      val repoTags: List[Tag] = List(Tag("E", LocalDateTime.now()))
      when(gitOpenTagDataSource.get(org, repoName)).thenReturn(Future.successful(repoTags))

      compositeTagsSource.get(org, repoName, repoType).futureValue shouldBe Success(repoTags)
    }

    "should fail gracefull by setting the Try to Failure state rather than the future" in new SetUp {
      val repoType            = "github-com"
      val repoTags: List[Tag] = List(Tag("E", LocalDateTime.now()))
      val ex                  = new RuntimeException("Bleeuurgh")

      when(gitOpenTagDataSource.get(org, repoName)).thenReturn(Future.failed(ex))

      compositeTagsSource.get(org, repoName, repoType).futureValue shouldBe Failure(ex)
    }

  }
}
