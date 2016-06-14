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
import scala.concurrent.duration._

class CachedTagsDataSourceSpec extends WordSpec with Matchers with ScalaFutures with MockitoSugar {

  val tagsDataSource = mock[TagsDataSource]
  val cachedDataSource = new CachedTagsDataSource(tagsDataSource) {
    override val refreshTimeInMillis = 1000.millis
  }

  "getAll" should {

    "load from the releases client and also cache the values" in {
      val reponame = "repoName"
      val owner = "owner"

      val result = List(Tag("tag1", LocalDateTime.now()))
      when(tagsDataSource.get(reponame, owner)).thenReturn(Future.successful(result))

      cachedDataSource.get(reponame, owner).futureValue should be(result)
      cachedDataSource.cache.get((reponame, owner)).futureValue shouldBe result

      verify(tagsDataSource, times(1)).get(reponame, owner)
    }

  }
}
