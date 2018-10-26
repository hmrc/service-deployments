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

package uk.gov.hmrc.servicedeployments.services

import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, TestData, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.servicedeployments.{DefaultPatienceConfig, ServiceDeploymentsConfig}
import uk.gov.hmrc.servicereleases.TestServiceDependenciesConfig

import scala.concurrent.Future

class ServiceRepositoriesServiceSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with GuiceOneAppPerTest
    with DefaultPatienceConfig {

  private val stubbedServiceDependenciesConfig = new TestServiceDependenciesConfig()

  val mockedTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
  implicit override def newAppForTest(testData: TestData): Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[ServiceDeploymentsConfig].toInstance(stubbedServiceDependenciesConfig),
        bind[TeamsAndRepositoriesConnector].toInstance(mockedTeamsAndRepositoriesConnector)
      )
      .build()

  "getAll" should {
    lazy val service = app.injector.instanceOf[ServiceRepositoriesService]

    "Convert to ServiceRepositories" in {
      val data = List(
        Service(
          "service-frontend",
          GithubUrl("github-open", "http://github.com/org2/service-frontend")
        ))

      when(mockedTeamsAndRepositoriesConnector.getAll()).thenReturn(Future.successful(data))

      service.getAll.futureValue shouldBe Map("service-frontend" -> Repository("org2"))
    }
  }

}
