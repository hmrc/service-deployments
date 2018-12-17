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

package uk.gov.hmrc.servicedeployments.deployments

import java.time.{LocalDateTime, ZoneOffset}

import com.github.tomakehurst.wiremock.http.RequestMethod
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.servicedeployments.{DefaultPatienceConfig, ServiceDeploymentsConfig, WireMockSpec}

import scala.concurrent.Future
import play.api.inject.bind
import uk.gov.hmrc.servicereleases.TestServiceDependenciesConfig


class ReleasesAppConnectorSpec
  extends WordSpec
    with Matchers
    with WireMockSpec
    with ScalaFutures
    with GuiceOneAppPerSuite
    with DefaultPatienceConfig
    with MockitoSugar {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[ServiceDeploymentsConfig].toInstance(new TestServiceDependenciesConfig(Map("deployments.api.url" -> endpointMockUrl))))
      .configure("metrics.jvm" -> false)
      .build()

  lazy val connector = app.injector.instanceOf[ReleasesAppConnector]

  "Get All" should {

    "get all deployments from the deployments app and return all deployments for the service in production" in {
      val `deployment 11.0.0 date` = LocalDateTime.now().minusDays(5).toEpochSecond(ZoneOffset.UTC)
      val `deployment 8.3.0 date` = LocalDateTime.now().minusMonths(5).toEpochSecond(ZoneOffset.UTC)

      givenRequestExpects(
        method = RequestMethod.GET,
        url = s"$endpointMockUrl/apps",
        willRespondWith = (200,
          Some(
            s"""
               |[
               |    {
               |        "an": "appA",
               |        "env": "prod-something",
               |        "fs": ${`deployment 11.0.0 date`},
               |        "ls": 1450877349,
               |        "ver": "11.0.0"
               |    },
               | {
               |         "an": "appA",
               |         "env": "prod-somethingOther",
               |         "fs": ${`deployment 11.0.0 date`},
               |        "ls": 1450877349,
               |        "ver": "11.0.0"
               |    },
               |    {
               |        "an": "appA",
               |        "env": "qa",
               |        "fs": 1449489675,
               |        "ls": 1450347910,
               |        "ver": "7.3.0"
               |    },
               |    {
               |        "an": "appB",
               |        "env": "qa",
               |        "fs": 1449489675,
               |        "ls": 1450347910,
               |        "ver": "7.3.0"
               |    },
               |    {
               |        "an": "appB",
               |        "env": "prod-something",
               |        "fs": 1449491521,
               |        "ls": 1450879982,
               |        "ver": "5.0.0"
               |    },
               |    {
               |        "an": "appA",
               |        "env": "prod-something",
               |        "deployer_audit": [[ "abc.xyz",${`deployment 8.3.0 date`}]],
               |        "fs": ${`deployment 8.3.0 date`},
               |        "ls": 1450347910,
               |        "ver": "8.3.0"
               |    }
               |
              |]
            """.stripMargin
          )))

      val results = connector.getAll.futureValue
      results.size shouldBe 6
      results.head shouldBe EnvironmentalDeployment(
        "prod-something", "appA", "11.0.0", LocalDateTime.ofEpochSecond(`deployment 11.0.0 date`, 0, ZoneOffset.UTC))

      val expectedDate: LocalDateTime = LocalDateTime.ofEpochSecond(`deployment 8.3.0 date`, 0, ZoneOffset.UTC)
      results.last shouldBe EnvironmentalDeployment(
        "prod-something", "appA", "8.3.0", expectedDate, Seq(Deployer(name = "abc.xyz", deploymentDate = expectedDate)))


    }


    "get all deployments from the deployments app should evict records which do not have first seen" in {
      val `deployment 11.0.0 date` = LocalDateTime.now().minusDays(5).toEpochSecond(ZoneOffset.UTC)
      val `deployment 8.3.0 date` = LocalDateTime.now().minusMonths(5).toEpochSecond(ZoneOffset.UTC)

      givenRequestExpects(
        method = RequestMethod.GET,
        url = s"$endpointMockUrl/apps",
        willRespondWith = (200,
          Some(
            s"""
               |[
               |    {
               |        "an": "appA",
               |        "env": "prod-something",
               |        "ls": 1450877349,
               |        "ver": "11.0.0"
               |    },
               | {
               |         "an": "appA",
               |         "env": "prod-somethingOther",
               |         "fs": ${`deployment 11.0.0 date`},
               |        "ls": 1450877349,
               |        "ver": "11.0.0"
               |    },
               |    {
               |        "an": "appA",
               |        "env": "qa",
               |        "fs": null,
               |        "ls": 1450347910,
               |        "ver": "7.3.0"
               |    }
               |]
            """.stripMargin
          )))

      val results = connector.getAll.futureValue
      results.size shouldBe 1

    }


  }


  "Get whatIsRunningWhere" should {
    "get apps and the environments they have been deployed to from the releases app" in {

      givenRequestExpects(
        method = RequestMethod.GET,
        url = s"$endpointMockUrl/whats-running-where",
        willRespondWith = (200,
          Some(
            s"""
               |[
               |    {
               |    "an": "app-1",
               |    "staging-datacentred-sal01": "1.15.0",
               |    "staging-skyscape-farnborough": "1.15.0",
               |    "production-skyscape-farnborough": "1.15.0",
               |    "qa-datacentred-sal01": "1.15.0",
               |    "production-datacentred-sal01": "1.15.0",
               |    "externaltest-datacentred-sal01": "1.14.0"
               |  }, {
               |    "an": "app-2",
               |    "staging-datacentred-sal01": "1.15.0",
               |    "staging-skyscape-farnborough": "1.15.0",
               |    "production-skyscape-farnborough": "1.15.0",
               |    "qa-datacentred-sal01": "1.15.0"
               |  }
               |]
            """.stripMargin
          )))

      val results = connector.whatIsRunningWhere.futureValue
      results.size shouldBe 2
      results(0).serviceName shouldBe "app-1"
      results(0).deployments.map(_.environmentMapping) should contain theSameElementsAs Set(
        EnvironmentMapping("staging", "staging"),
        EnvironmentMapping("production", "production"),
        EnvironmentMapping("qa", "qa"),
        EnvironmentMapping("external test", "externaltest"))


      results(1).serviceName shouldBe "app-2"
      results(1).deployments.map(_.environmentMapping) should contain theSameElementsAs Set(
        EnvironmentMapping("staging", "staging"),
        EnvironmentMapping("production", "production"),
        EnvironmentMapping("qa", "qa"))


    }

  }


  "DeploymentsApiConnector.whatIsRunningWhere" should {
    "deserialize objects from the releases app API" in {
      val deploymentsApiBase = endpointMockUrl

      val deploymentsApiConnector = app.injector.instanceOf[ReleasesAppConnector] //new ReleasesAppConnector(deploymentsApiBase)

      givenRequestExpects(
        method = RequestMethod.GET,
        url = s"$endpointMockUrl/whats-running-where",
        headers = List("Accept" -> "application/json"),
        willRespondWith = (200,
          Some(
            s"""
               |[
               |    {
               |        "staging-skyscape-farnborough": "0.7.0",
               |        "staging-datacentred-sal01": "0.8.0",
               |        "an": "bbsi-stubs"
               |    },
               |    {
               |        "staging-skyscape-farnborough": "0.0.33",
               |        "production-skyscape-farnborough": "0.0.35",
               |        "an": "benefits-stub"
               |    }
               |]
            """.stripMargin
          )))


      import ServiceDeploymentInformation.Deployment

      val results: Future[List[ServiceDeploymentInformation]] = deploymentsApiConnector.whatIsRunningWhere

      results.futureValue shouldBe List(
        ServiceDeploymentInformation(
          "bbsi-stubs",
          Set(
            Deployment(
              EnvironmentMapping("staging", "staging"),
              "skyscape-farnborough",
              "0.7.0"),
            Deployment(
              EnvironmentMapping("staging", "staging"),
              "datacentred-sal01",
              "0.8.0"
            ))),
        ServiceDeploymentInformation(
          "benefits-stub",
          Set(
            Deployment(
              EnvironmentMapping("staging", "staging"),
              "skyscape-farnborough",
              "0.0.33"),
            Deployment(
              EnvironmentMapping("production", "production"),
              "skyscape-farnborough",
              "0.0.35"
            )
          )
        )
      )
    }
  }
}
