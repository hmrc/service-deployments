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

package uk.gov.hmrc.servicedeployments.services

import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.OneAppPerTest
import uk.gov.hmrc.servicedeployments.DefaultPatienceConfig

import scala.concurrent.Future

class DefaultServiceRepositoriesServiceSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures with OneAppPerTest with DefaultPatienceConfig {

  "getAll" should {

    val dataSource = mock[ServiceDataSource]
    val service = new DefaultServiceRepositoriesService(dataSource)

    "Convert to ServiceRepositories" in {
      val data = List(
        Service("service-frontend", List(
          GithubUrl("github","https://github.some.host.url.gov.uk/org1-org/service-frontend"),
          GithubUrl("github-open", "http://github.com/org2/service-frontend"))))

      when(dataSource.getAll()).thenReturn(Future.successful(data))

      service.getAll().futureValue shouldBe Map(
        "service-frontend" -> List(
          Repository("org1-org", "github"),
          Repository("org2", "github-open")))
    }

    "convert to serviceInfo when no gitenterprise url" in {
      val data = List(
        Service("service-frontend", List(
          GithubUrl("github-open", "http://github.com/org2/service-frontend"))))

      when(dataSource.getAll()).thenReturn(Future.successful(data))

      service.getAll().futureValue shouldBe Map(
        "service-frontend" -> List(Repository("org2", "github-open")))
    }

    "convert to serviceInfo when no git open url" in {
      val data = List(
        Service("service-frontend", List(
          GithubUrl("github", "http://github.com/org1-org/service-frontend"))))

      when(dataSource.getAll()).thenReturn(Future.successful(data))

      service.getAll().futureValue shouldBe Map(
        "service-frontend" -> List(Repository("org1-org", "github")))
    }
  }

}
