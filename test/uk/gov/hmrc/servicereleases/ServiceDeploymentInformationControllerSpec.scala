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

package uk.gov.hmrc.servicereleases


import org.mockito.Mockito._
import org.scalatest.OptionValues
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import play.api.mvc.{Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.servicedeployments.deployments.EnvironmentMapping
import uk.gov.hmrc.servicedeployments.deployments.ServiceDeploymentInformation.Deployment
import uk.gov.hmrc.servicedeployments.{WhatIsRunningWhereController, WhatIsRunningWhereModel, WhatIsRunningWhereRepository}

import scala.concurrent.Future

class ServiceDeploymentInformationControllerSpec extends PlaySpec with MockitoSugar with Results with OptionValues{

  val whatIsRunningWhereRepo = mock[WhatIsRunningWhereRepository]

  val controller = new WhatIsRunningWhereController {
    override def whatIsRunningWhereRepository: WhatIsRunningWhereRepository = whatIsRunningWhereRepo
  }


  "WhatIsRunningWhereController.forApplication" should {
    "retrieve list of all WhatsRunningWhere for an application" in {

      val deployments = Set(
        Deployment(EnvironmentMapping("qa", "qa"), "datacentred", "0.0.1"),
        Deployment(EnvironmentMapping("production", "production"), "skyscape-farnborough", "0.0.2")
      )

      when(whatIsRunningWhereRepo.getForService("appName-1")).thenReturn(
        Future.successful(Some(
          WhatIsRunningWhereModel(serviceName = "appName-1", deployments = deployments)
        ))
      )

      val result: Future[Result] = controller.forApplication("appName-1")(FakeRequest())

      val json = contentAsJson(result)
      val jsonResult: JsObject = json.as[JsObject]

      jsonResult mustBe JsObject(Map(
        "serviceName" -> JsString("appName-1"),
        "deployments" -> JsArray(
          Seq(
            JsObject(
              Map(
                "environmentMapping" -> JsObject(
                  Map(
                    "name" -> JsString("qa"),
                    "releasesAppId" -> JsString("qa")
                  )
                ),
                "datacentre" -> JsString("datacentred"),
                "version" -> JsString("0.0.1")
              )),
            JsObject(
              Map(
                "environmentMapping" -> JsObject(
                  Map(
                    "name" -> JsString("production"),
                    "releasesAppId" -> JsString("production")
                  )
                ),
                "datacentre" -> JsString("skyscape-farnborough"),
                "version" -> JsString("0.0.2")
              ))
          ))))
    }
  }
}
