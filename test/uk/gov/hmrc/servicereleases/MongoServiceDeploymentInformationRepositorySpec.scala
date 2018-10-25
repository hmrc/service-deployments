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

package uk.gov.hmrc.servicereleases

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.mockito.Mockito
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.servicedeployments.deployments.ServiceDeploymentInformation.Deployment
import uk.gov.hmrc.servicedeployments.deployments.{EnvironmentMapping, ServiceDeploymentInformation}
import uk.gov.hmrc.servicedeployments.{FutureHelpers, ServiceDeploymentsConfig, WhatIsRunningWhereModel, WhatIsRunningWhereRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class MongoServiceDeploymentInformationRepositorySpec
    extends FunSpec
    with Matchers
    with LoneElement
    with MongoSpecSupport
    with ScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with GuiceOneAppPerTest
    with MockitoSugar {

  implicit override def newAppForTest(testData: TestData): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[ServiceDeploymentsConfig].toInstance(new TestServiceDependenciesConfig()))
      .build()

  def await[A](future: Future[A]) = Await.result(future, 5 seconds)

  val mockedConnector = mock[MongoConnector]
  Mockito.when(mockedConnector.db).thenReturn(mongo)

  val reactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector =
      mockedConnector
  }

  private val metrics: Metrics = new Metrics() {
    override def defaultRegistry = new MetricRegistry
    override def toJson          = "XxX"
  }

  val mongoWhatIsRunningWhereRepository =
    new WhatIsRunningWhereRepository(reactiveMongoComponent, new FutureHelpers((metrics)))

  override def beforeEach() {
    await(mongoWhatIsRunningWhereRepository.drop)
  }

  describe("update") {
    it("should update already existing items") {

      val whatIsRunningWhere =
        ServiceDeploymentInformation(
          "app-1",
          Set(
            Deployment(
              EnvironmentMapping("qa", "qa"),
              "datacentred",
              "0.0.1"
            ),
            Deployment(
              EnvironmentMapping("production", "production"),
              "datacentred",
              "0.0.1"
            )
          )
        )

      await(mongoWhatIsRunningWhereRepository.update(whatIsRunningWhere))

      val all = await(mongoWhatIsRunningWhereRepository.findAll())

      val savedWhatIsRunningWhere: WhatIsRunningWhereModel = all.loneElement

      val changedDeployments =
        Set(
          Deployment(
            EnvironmentMapping("staging", "staging"),
            "skyscape-farnborough",
            "0.0.2"
          )
        )

      await(
        mongoWhatIsRunningWhereRepository
          .update(whatIsRunningWhere.copy(deployments = changedDeployments)))

      val allUpdated = await(mongoWhatIsRunningWhereRepository.getAll)
      allUpdated.size shouldBe 1
      val updatedWhatIsRunningWhere: WhatIsRunningWhereModel = allUpdated.loneElement

      updatedWhatIsRunningWhere.serviceName shouldBe whatIsRunningWhere.serviceName
      updatedWhatIsRunningWhere.deployments shouldBe changedDeployments

    }

  }

  describe("getAll") {
    import play.api.libs.json._
    import reactivemongo.play.json._

    import scala.concurrent.ExecutionContext.Implicits.global

    it("return all the whatIsRunningWhere records") {

      ServiceDeploymentInformation(
        "app-1",
        Set(
          Deployment(
            EnvironmentMapping("qa", "qa"),
            "datacentred",
            "0.0.1"
          ),
          Deployment(
            EnvironmentMapping("production", "production"),
            "datacentred",
            "0.0.1"
          )
        )
      )

      val deployments = Set(
        Deployment(
          EnvironmentMapping("qa", "qa"),
          "datacentred",
          "0.0.1"
        ),
        Deployment(
          EnvironmentMapping("production", "production"),
          "skyscape-farnborough",
          "0.0.2"
        )
      )

      await(
        mongoWhatIsRunningWhereRepository.collection.insert(
          Json.obj(
            "serviceName" -> "app-123",
            "deployments" -> deployments
          )))

      val whatIsRunningWheres: Seq[WhatIsRunningWhereModel] = await(mongoWhatIsRunningWhereRepository.getAll)

      whatIsRunningWheres.size             shouldBe 1
      whatIsRunningWheres.head.serviceName shouldBe "app-123"
      whatIsRunningWheres.head.deployments shouldBe deployments
    }
  }

  describe("allGroupedByName") {
    import play.api.libs.json._
    import reactivemongo.play.json._

    import scala.concurrent.ExecutionContext.Implicits.global

    it("should return all the whatIsRunningWhere grouped by application name") {

      val deployments1 = Set(
        Deployment(EnvironmentMapping("qa", "qa"), "datacentred", "0.0.1"),
        Deployment(EnvironmentMapping("production", "production"), "skyscape-farnborough", "0.0.2")
      )

      val deployments2 = Set(
        Deployment(EnvironmentMapping("staging", "staging"), "datacentred", "0.0.3")
      )

      await(
        mongoWhatIsRunningWhereRepository.collection.insert(
          Json.obj(
            "serviceName" -> "app-1",
            "deployments" -> deployments1
          )))
      await(
        mongoWhatIsRunningWhereRepository.collection.insert(
          Json.obj(
            "serviceName" -> "app-2",
            "deployments" -> deployments2
          )))

      val whatIsRunningWheres: Map[String, Seq[WhatIsRunningWhereModel]] =
        await(mongoWhatIsRunningWhereRepository.allGroupedByName)

      whatIsRunningWheres.size                      shouldBe 2
      whatIsRunningWheres.keys                      should contain theSameElementsAs Seq("app-1", "app-2")
      whatIsRunningWheres("app-1").size             shouldBe 1
      whatIsRunningWheres("app-1").head.deployments should contain theSameElementsAs deployments1

      whatIsRunningWheres("app-2").size             shouldBe 1
      whatIsRunningWheres("app-2").head.deployments should contain theSameElementsAs deployments2
    }
  }

  describe("getForApplication") {
    import play.api.libs.json._
    import reactivemongo.play.json._

    import scala.concurrent.ExecutionContext.Implicits.global

    it("return the whatIsRunningWhere for the given application name") {

      val deployments1 = Set(
        Deployment(EnvironmentMapping("qa", "qa"), "datacentred", "0.0.1"),
        Deployment(EnvironmentMapping("production", "production"), "skyscape-farnborough", "0.0.2")
      )

      val deployments2 = Set(
        Deployment(EnvironmentMapping("staging", "staging"), "datacentred", "0.0.3")
      )

      await(
        mongoWhatIsRunningWhereRepository.collection.insert(
          Json.obj(
            "serviceName" -> "app-1",
            "deployments" -> deployments1
          )))
      await(
        mongoWhatIsRunningWhereRepository.collection.insert(
          Json.obj(
            "serviceName" -> "app-2",
            "deployments" -> deployments2
          )))

      val whatIsRunningWheres: Option[WhatIsRunningWhereModel] =
        await(mongoWhatIsRunningWhereRepository.getForService("app-1"))

      whatIsRunningWheres.value.serviceName shouldBe "app-1"
      whatIsRunningWheres.value.deployments shouldBe deployments1
    }

    it("be case insensitive") {

      val deployments1 = Set(
        Deployment(EnvironmentMapping("qa", "qa"), "datacentred", "0.0.1")
      )

      await(
        mongoWhatIsRunningWhereRepository.collection.insert(
          Json.obj(
            "serviceName" -> "app-1",
            "deployments" -> deployments1
          )))

      val whatIsRunningWheres: Option[WhatIsRunningWhereModel] =
        await(mongoWhatIsRunningWhereRepository.getForService("APP-1"))

      whatIsRunningWheres                   shouldBe defined
      whatIsRunningWheres.value.serviceName shouldBe "app-1"
    }
  }

  describe("clearAllData") {
    import play.api.libs.json._
    import reactivemongo.play.json._

    import scala.concurrent.ExecutionContext.Implicits.global

    it("remove all the whatIsRunningWhere records") {

      val deployments1 = Set(
        Deployment(EnvironmentMapping("qa", "qa"), "datacentred", "0.0.1"),
        Deployment(EnvironmentMapping("production", "production"), "skyscape-farnborough", "0.0.2")
      )

      val deployments2 = Set(
        Deployment(EnvironmentMapping("staging", "staging"), "datacentred", "0.0.3")
      )

      await(
        mongoWhatIsRunningWhereRepository.collection.insert(
          Json.obj(
            "serviceName" -> "app-1",
            "deployments" -> deployments1
          )))
      await(
        mongoWhatIsRunningWhereRepository.collection.insert(
          Json.obj(
            "serviceName" -> "app-2",
            "deployments" -> deployments2
          )))

      await(mongoWhatIsRunningWhereRepository.clearAllData)

      val whatIsRunningWheres = await(mongoWhatIsRunningWhereRepository.getAll)

      whatIsRunningWheres.size shouldBe 0

    }
  }

}
