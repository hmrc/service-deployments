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

package uk.gov.hmrc.servicereleases.deployments

import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.{JsError, Json}
import uk.gov.hmrc.servicedeployments.deployments.ServiceDeploymentInformation.Deployment
import uk.gov.hmrc.servicedeployments.deployments.{EnvironmentMapping, ServiceDeploymentInformation}

class ServiceDeploymentInformationSpec extends FunSpec with Matchers {

  describe("parsing the json")  {

    it("should return appname and all environments correctly") {
      val json = """{
                    | "staging-datacentred-sal01": "0.90.0",
                    | "staging-skyscape-farnborough": "0.91.0",
                    | "production-skyscape-farnborough": "0.92.0",
                    | "qa-datacentred-sal01": "0.93.0",
                    | "production-datacentred-sal01": "0.94.0",
                    | "an": "app123",
                    | "externaltest-datacentred-sal01": "0.95.0"
                    |}""".stripMargin


      val whatIsRunningWhere = Json.parse(json).as[ServiceDeploymentInformation]
      whatIsRunningWhere.serviceName shouldBe "app123"
      whatIsRunningWhere.deployments should contain theSameElementsAs Set(
        Deployment(EnvironmentMapping("staging", "staging"), "datacentred-sal01", "0.90.0"),
        Deployment(EnvironmentMapping("staging", "staging"), "skyscape-farnborough", "0.91.0"),
        Deployment(EnvironmentMapping("production", "production"), "skyscape-farnborough", "0.92.0"),
        Deployment(EnvironmentMapping("qa", "qa"), "datacentred-sal01", "0.93.0"),
        Deployment(EnvironmentMapping("production", "production"), "datacentred-sal01", "0.94.0"),
        Deployment(EnvironmentMapping("external test", "externaltest"), "datacentred-sal01", "0.95.0"))
    }

    it("should error if app name is missing in payload") {
      val j = """{
                |  "production-skyscape-farnborough": "0.90.0",
                |  "externaltest-datacentred-sal01": "0.98.0"
                |}""".stripMargin


      val whatIsRunningWhere = Json.parse(j).validate[ServiceDeploymentInformation]

      whatIsRunningWhere match {
        case JsError(e) => e.toString() should include("'an' (i.e. application name) field is missing in json")
        case _ => fail("was expecting a json error")
      }
    }

    it("should NOT error if no environments are found in payload") {
      val j = """{
                |  "an": "appName"
                |}""".stripMargin


      val whatIsRunningWhere = Json.parse(j).as[ServiceDeploymentInformation]

      whatIsRunningWhere shouldBe ServiceDeploymentInformation("appName", Set.empty)
    }

    it("should ignore non-left environments when serialising") {
      val json = """{
                   | "an": "app123",
                   | "staging-datacentred-sal01": "0.90.0",
                   | "prod-right": "0.95.0"
                   |}""".stripMargin
      val whatIsRunningWhere = Json.parse(json).as[ServiceDeploymentInformation]
      whatIsRunningWhere.serviceName shouldBe "app123"
      whatIsRunningWhere.deployments shouldEqual Set(
        Deployment(EnvironmentMapping("staging", "staging"), "datacentred-sal01", "0.90.0")
      )
    }
  }
}
