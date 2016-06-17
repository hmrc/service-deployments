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

package uk.gov.hmrc.servicereleases.tags

import java.time.LocalDateTime

import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Future

class TagsServiceSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures {

  trait SetUp {
    val gitEnterpriseTagDataSource = mock[TagsDataSource]
    val gitOpenTagDataSource = mock[TagsDataSource]
    val compositeTagsSource = new DefaultTagsService(gitEnterpriseTagDataSource, gitOpenTagDataSource)
  }

  "getAll" should {

    val repoName = "service"
    val org = "org"

    "use enterprise data source if RepoType is Enterprise" in new SetUp {
      val repoType = "github"
      val repoTags: List[Tag] = List(Tag("E", LocalDateTime.now()))
      when(gitEnterpriseTagDataSource.get(org, repoName)).thenReturn(Future.successful(repoTags))

      compositeTagsSource.get(org, repoName, repoType).futureValue shouldBe repoTags
      verifyZeroInteractions(gitOpenTagDataSource)
    }

    "use open data source if RepoType is Open" in new SetUp {
      val repoType = "github-open"
      val repoTags: List[Tag] = List(Tag("E", LocalDateTime.now()))
      when(gitOpenTagDataSource.get(org, repoName)).thenReturn(Future.successful(repoTags))

      compositeTagsSource.get(org, repoName, repoType).futureValue shouldBe repoTags
      verifyZeroInteractions(gitEnterpriseTagDataSource)
    }

  }
}
