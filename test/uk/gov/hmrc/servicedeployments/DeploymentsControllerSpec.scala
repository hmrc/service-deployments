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

package uk.gov.hmrc.servicedeployments

import java.time.{LocalDateTime, ZoneOffset}

import org.mockito.Mockito._
import org.scalatest.Matchers._
import org.scalatest.OptionValues
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import uk.gov.hmrc.servicedeployments.deployments.Deployer

import scala.concurrent.Future


class DeploymentsControllerSpec extends PlaySpec with MockitoSugar with Results with OptionValues {

  "forService" should {

    "return OK with retrieved list of all deployments for a service" in new Setup {

      when(deploymentsRepo.deploymentsForServices(Set("serviceName"))).thenReturn(Future.successful(Seq(
        Deployment(
          name           = "serviceName",
          version        = "1",
          creationDate   = Some(now),
          productionDate = now,
          _id            = Some(BSONObjectID.generate)
        ),
        Deployment(
          name           = "serviceName",
          version        = "2",
          creationDate   = Some(now),
          productionDate = now,
          _id            = Some(BSONObjectID.generate)
        ),
        Deployment(
          name           = "serviceName",
          version        = "3",
          creationDate   = Some(now),
          productionDate = now,
          _id            = Some(BSONObjectID.generate),
          deployers      = Seq(Deployer("xyz.abc", now))
        )
      )))

      val result = controller.forService("serviceName")(FakeRequest())

      status(result) shouldBe OK

      val jsonResult = contentAsJson(result).as[Seq[JsObject]]
      jsonResult should contain theSameElementsAs Seq(
        Json.obj(
          "name"           -> "serviceName",
          "version"        -> "1",
          "creationDate"   -> now.toEpochSecond(ZoneOffset.UTC),
          "productionDate" -> now.toEpochSecond(ZoneOffset.UTC),
          "deployers"      -> JsArray(List.empty)
        ),
        Json.obj(
          "name"           -> "serviceName",
          "version"        -> "2",
          "creationDate"   -> now.toEpochSecond(ZoneOffset.UTC),
          "productionDate" -> now.toEpochSecond(ZoneOffset.UTC),
          "deployers"      -> JsArray(List.empty)
        ),
        Json.obj(
          "name"           -> "serviceName",
          "version"        -> "3",
          "creationDate"   -> now.toEpochSecond(ZoneOffset.UTC),
          "productionDate" -> now.toEpochSecond(ZoneOffset.UTC),
          "deployers" -> JsArray(
            Seq(Json.obj("name" -> "xyz.abc", "deploymentDate" -> now.toEpochSecond(ZoneOffset.UTC))))
        )
      )
    }

    "return NOT_FOUND when no deployments for a service is found" in new Setup {

      when(deploymentsRepo.deploymentsForServices(Set("serviceName"))).thenReturn(Future.successful(Nil))

      val result = controller.forService("serviceName")(FakeRequest())

      status(result) shouldBe NOT_FOUND
    }
  }

  "getAll" should {

    "return OK with all deployments" in new Setup {
      when(deploymentsRepo.getAllDeployments).thenReturn(
        Future.successful(
          Seq(
            Deployment(name = "name1", version = "version1", creationDate = None, productionDate = now),
            Deployment(name = "name2", version = "version2", creationDate = None, productionDate = now)
          )
        )
      )

      val result = controller.getAll(FakeRequest())

      status(result)                          shouldBe OK
      contentAsJson(result).as[Seq[JsObject]] should contain theSameElementsAs Seq(
        Json.obj(
          "name"           -> "name1",
          "version"        -> "version1",
          "productionDate" -> now.toEpochSecond(ZoneOffset.UTC),
          "deployers"      -> JsArray(List.empty)
        ),
        Json.obj(
          "name"           -> "name2",
          "version"        -> "version2",
          "productionDate" -> now.toEpochSecond(ZoneOffset.UTC),
          "deployers"      -> JsArray(List.empty)
        )
      )
    }

    "return empty list when no deployments are found" in new Setup {

      when(deploymentsRepo.getAllDeployments).thenReturn(Future.successful(Nil))

      val result = controller.getAll(FakeRequest())

      status(result) shouldBe OK

      contentAsJson(result) should be(Json.parse("{}"))
    }
  }

  "forServices" should {

    "return OK with all deployments for given serviceNames" in new Setup {

      val serviceNames = Set("service1", "service2")

      when(deploymentsRepo.deploymentsForServices(serviceNames)).thenReturn(
        Future.successful(
          Seq(
            Deployment(name = "name1", version = "version1", creationDate = None, productionDate = now),
            Deployment(name = "name2", version = "version2", creationDate = None, productionDate = now)
          )
        )
      )

      val result = controller.forServices(FakeRequest().withBody(Json.arr("service1", "service2")))

      status(result)                          shouldBe OK
      contentAsJson(result).as[Seq[JsObject]] should contain theSameElementsAs Seq(
        Json.obj(
          "name"           -> "name1",
          "version"        -> "version1",
          "productionDate" -> now.toEpochSecond(ZoneOffset.UTC),
          "deployers"      -> JsArray(List.empty)
        ),
        Json.obj(
          "name"           -> "name2",
          "version"        -> "version2",
          "productionDate" -> now.toEpochSecond(ZoneOffset.UTC),
          "deployers"      -> JsArray(List.empty)
        )
      )
    }

    "return NOT_FOUND when no service names given" in new Setup {

      val result = controller.forServices(FakeRequest().withBody(Json.arr()))

      status(result) shouldBe BAD_REQUEST
    }

    "return an empty list when no deployments are found for the service names supplied" in new Setup {

      when(deploymentsRepo.deploymentsForServices(Set("service1", "service2")))
        .thenReturn(Future.successful(Nil))

      val result = controller.forServices(FakeRequest().withBody(Json.arr("service1", "service2")))

      status(result) shouldBe OK

      contentAsJson(result) should be(Json.parse("{}"))
    }
  }

  private trait Setup {

    val now: LocalDateTime = LocalDateTime.now()

    val deploymentsRepo = mock[DeploymentsRepository]
    val controller      = new DeploymentsController(mock[UpdateScheduler], deploymentsRepo, stubMessagesControllerComponents())
  }

}
