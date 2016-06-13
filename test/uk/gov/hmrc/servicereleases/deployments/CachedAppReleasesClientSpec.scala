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

package uk.gov.hmrc.servicereleases.deployments

import java.time.LocalDateTime

import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Future
import scala.concurrent.duration._

class CachedAppReleasesClientSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures {

  val connector = mock[ReleasesApiConnector]
  val cachedClient = new CachedDeploymentsDataSource(connector) {
    override val refreshTimeInMillis = 100.millis
  }

  "Get All" should {

    "load from the releases client and also cache the values" in {
      val result = List(Deployment("", "appName", "1.0.0", LocalDateTime.now()))

      when(connector.getAll).thenReturn(Future.successful(result))

      cachedClient.getAll.futureValue should be(result)
      cachedClient.cache.get("appReleases").futureValue shouldBe result

      verify(connector, times(1)).getAll
    }

  }
}
