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
import uk.gov.hmrc.servicedeployments.deployments.Environment
import uk.gov.hmrc.servicedeployments.{WhatIsRunningWhereController, WhatIsRunningWhereModel, WhatIsRunningWhereRepository}

import scala.concurrent.Future

class WhatIsRunningWhereControllerSpec extends PlaySpec with MockitoSugar with Results with OptionValues{

  val whatIsRunningWhereRepo = mock[WhatIsRunningWhereRepository]

  val controller = new WhatIsRunningWhereController {
    override def whatIsRunningWhereRepository: WhatIsRunningWhereRepository = whatIsRunningWhereRepo
  }


  "forApplication" should {
    "retrieve list of all whats running where for an application" in {

      when(whatIsRunningWhereRepo.getForApplication("appName-1")).thenReturn(
        Future.successful(Some(Seq(
          WhatIsRunningWhereModel(applicationName = "appName-1", environments = Set(Environment("qa", "qa"), Environment("prod", "prod"))))
        ))
      )

      val result: Future[Result] = controller.forApplication("appName-1")(FakeRequest())

      val jsonResult: Seq[JsObject] = contentAsJson(result).as[Seq[JsObject]]

      jsonResult.size mustBe  1
      jsonResult.map(_.value) mustBe Seq(
        Map(
          "applicationName" -> JsString("appName-1"),
          "environments" -> JsArray(
            Seq(
              JsObject(Map("name" -> JsString("qa"), "whatIsRunningWhereId" -> JsString("qa"))),
              JsObject(Map("name" -> JsString("prod"), "whatIsRunningWhereId" -> JsString("prod")))
            ))))

    }
  }
}
