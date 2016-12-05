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

package uk.gov.hmrc.servicedeployments

import java.time.{ZoneOffset, LocalDateTime}

import org.mockito.Mockito._
import org.scalatest.{Matchers, OptionValues}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.libs.json._
import play.api.mvc.{Result, AnyContentAsEmpty, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future

class DeploymentControllerSpec extends PlaySpec with MockitoSugar with Results with OptionValues{

  val timestamp = LocalDateTime.of(2016, 4, 5, 12, 57, 10)

  val deploymentRepo = mock[DeploymentsRepository]

  val controller = new DeploymentsController {
    override def deploymentsRepository: DeploymentsRepository = deploymentRepo
  }


  "forService" should {
    "retrieve list of all deployments for a service" in {

      val now: LocalDateTime = LocalDateTime.now()

      when(deploymentRepo.getForService("serviceName")).thenReturn(
        Future.successful(Some(Seq(
          Deployment(name = "serviceName", version = "1", creationDate = Some(now), productionDate = now, _id = Some(BSONObjectID.generate)),
          Deployment(name = "serviceName", version = "2", creationDate = Some(now), productionDate = now, _id = Some(BSONObjectID.generate)),
          Deployment(name = "serviceName", version = "3", creationDate = Some(now), productionDate = now, _id = Some(BSONObjectID.generate))
        ))
        ))

      val result: Future[Result] = controller.forService("serviceName")(FakeRequest())

      val jsonResult: Seq[JsObject] = contentAsJson(result).as[Seq[JsObject]]

      jsonResult.size mustBe  3
      jsonResult.map(_.value) mustBe Seq(
        Map("name" -> JsString("serviceName"), "version" -> JsString("1"), "creationDate" -> JsNumber(now.toEpochSecond(ZoneOffset.UTC)), "productionDate" -> JsNumber(now.toEpochSecond(ZoneOffset.UTC))),
        Map("name" -> JsString("serviceName"), "version" -> JsString("2"), "creationDate" -> JsNumber(now.toEpochSecond(ZoneOffset.UTC)), "productionDate" -> JsNumber(now.toEpochSecond(ZoneOffset.UTC))),
        Map("name" -> JsString("serviceName"), "version" -> JsString("3"), "creationDate" -> JsNumber(now.toEpochSecond(ZoneOffset.UTC)), "productionDate" -> JsNumber(now.toEpochSecond(ZoneOffset.UTC)))
      )
    }
  }
}
