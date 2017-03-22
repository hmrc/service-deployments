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
import uk.gov.hmrc.servicedeployments.deployments.WhatIsRunningWhere

class WhatIsRunningWhereSpec extends FunSpec with Matchers {

  describe("parsing the json to ")  {

    it("should return appname and all environments correctly") {
      val j = """{
                |    "staging-datacentred-sal01": "0.90.0",
                |    "staging-skyscape-farnborough": "0.90.0",
                |    "production-skyscape-farnborough": "0.90.0",
                |    "qa-datacentred-sal01": "0.100.0",
                |    "production-datacentred-sal01": "0.90.0",
                |    "an": "app123",
                |    "externaltest-datacentred-sal01": "0.98.0"
                |  }""".stripMargin


      val whatIsRunningWhere = Json.parse(j).as[WhatIsRunningWhere]
      whatIsRunningWhere.applicationName shouldBe "app123"
      whatIsRunningWhere.environments should contain theSameElementsAs Seq("staging", "production", "qa", "externaltest")
    }

    it("should not return environment names that the is not deployed to") {
      val j = """{
                |    "production-skyscape-farnborough": "0.90.0",
                |    "an": "app123",
                |    "externaltest-datacentred-sal01": "0.98.0"
                |  }""".stripMargin


      val whatIsRunningWhere = Json.parse(j).as[WhatIsRunningWhere]
      
      whatIsRunningWhere.environments should contain theSameElementsAs Seq("production", "externaltest")
    }

    it("should error if app name is missing in payload") {
      val j = """{
                |    "production-skyscape-farnborough": "0.90.0",
                |    "externaltest-datacentred-sal01": "0.98.0"
                |  }""".stripMargin


      val whatIsRunningWhere = Json.parse(j).validate[WhatIsRunningWhere]

      whatIsRunningWhere match {
        case JsError(e) => e.toString() should include("'an' (i.e. application name) field is missing in json")
        case _ => fail("was expecting a json error")
      }
    }

    it("should NOT error if no environments are found in payload") {
      val j = """{
                |    "an": "appName"
                |  }""".stripMargin


      val whatIsRunningWhere = Json.parse(j).as[WhatIsRunningWhere]

      whatIsRunningWhere shouldBe WhatIsRunningWhere("appName", Nil)
    }
  }
}
