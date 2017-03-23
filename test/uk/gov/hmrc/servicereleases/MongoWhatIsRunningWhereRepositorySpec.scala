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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, LoneElement, OptionValues}
import org.scalatestplus.play.OneAppPerTest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.servicedeployments.deployments.WhatIsRunningWhere

import scala.concurrent.ExecutionContext.Implicits.global

class MongoWhatIsRunningWhereRepositorySpec extends UnitSpec with LoneElement with MongoSpecSupport with ScalaFutures with OptionValues with BeforeAndAfterEach with OneAppPerTest {




  val mongoWhatIsRunningWhereRepository = new MongoWhatIsRunningWhereRepository(mongo)

  override def beforeEach() {
    await(mongoWhatIsRunningWhereRepository.drop)
  }

  "update" should {
    "update already existing items" in {

      val whatIsRunningWhere = WhatIsRunningWhere("app-1", Seq("qa", "production"))
      await(mongoWhatIsRunningWhereRepository.update(whatIsRunningWhere))

      val all = await(mongoWhatIsRunningWhereRepository.findAll())

      val savedWhatIsRunningWhere: WhatIsRunningWhereModel = all.loneElement

      await(mongoWhatIsRunningWhereRepository.update(whatIsRunningWhere.copy(environments= Seq("staging"))))

      val allUpdated = await(mongoWhatIsRunningWhereRepository.getAll)
      allUpdated.size shouldBe 1
      val updatedWhatIsRunningWhere: WhatIsRunningWhereModel = allUpdated.loneElement

      updatedWhatIsRunningWhere.applicationName shouldBe whatIsRunningWhere.applicationName
      updatedWhatIsRunningWhere.environments shouldBe Seq("staging")

    }

  }


    "getAll" should {
      import uk.gov.hmrc.mongo.json.ReactiveMongoFormats._
      import scala.concurrent.ExecutionContext.Implicits.global
      import play.api.libs.json._
      import reactivemongo.json._

      "return all the deployments when already saved deployments do not have 'deployers' " in {
      import play.api.libs.json._

      await(mongoWhatIsRunningWhereRepository.collection.insert(Json.obj(
        "applicationName" -> "app-123" ,
        "environments" -> Seq("qa", "production")
      )))

      val deployments: Seq[WhatIsRunningWhereModel] = await(mongoWhatIsRunningWhereRepository.getAll)

      deployments.size shouldBe 1
      deployments.head.applicationName shouldBe "app-123"
      deployments.head.environments shouldBe Seq("qa", "production")

    }
//!@
//    "return all the deployments in descending order of productionDate" in {
//
//      val now: LocalDateTime = LocalDateTime.now()
//
//      await(mongoWhatIsRunningWhereRepository.add(Deployment("test2", "v2", None, productionDate = now.minusDays(6))))
//      await(mongoWhatIsRunningWhereRepository.add(Deployment("test3", "v3", None, productionDate = now.minusDays(5))))
//      await(mongoWhatIsRunningWhereRepository.add(Deployment("test1", "v1", None, productionDate = now.minusDays(10))))
//      await(mongoWhatIsRunningWhereRepository.add(Deployment("test4", "v4", None, productionDate = now.minusDays(2))))
//      await(mongoWhatIsRunningWhereRepository.add(Deployment("test5", "vSomeOther1", None, now.minusDays(2), Some(1))))
//      await(mongoWhatIsRunningWhereRepository.add(Deployment("test5", "vSomeOther2", None, now, Some(1), None, Seq(Deployer("xyz.abc", now)), None)))
//
//      val result: Seq[Deployment] = await(mongoWhatIsRunningWhereRepository.getAllDeployments)
//
//      result.map(x => (x.name, x.version, x.deployers.map(_.name))) shouldBe Seq(
//        ("test5", "vSomeOther2", Seq("xyz.abc")),
//        ("test4", "v4", Nil),
//        ("test5", "vSomeOther1", Nil),
//        ("test3", "v3", Nil),
//        ("test2", "v2", Nil),
//        ("test1", "v1", Nil)
//      )
//
//    }
  }

//  "getForService" should {
//    "return deployments for a service sorted in descending order of productionDate" in {
//      val now: LocalDateTime = LocalDateTime.now()
//
//      await(mongoWhatIsRunningWhereRepository.add(Deployment("randomService", "vSomeOther1", None, now, Some(1))))
//      await(mongoWhatIsRunningWhereRepository.add(Deployment("test", "v1", None, productionDate = now.minusDays(10), interval = Some(1))))
//      await(mongoWhatIsRunningWhereRepository.add(Deployment("test", "v2", None, productionDate = now.minusDays(6), interval = Some(1))))
//      await(mongoWhatIsRunningWhereRepository.add(Deployment("test", "v3", None, productionDate = now.minusDays(5), Some(1))))
//      await(mongoWhatIsRunningWhereRepository.add(Deployment("test", "v4", None, productionDate = now.minusDays(2), Some(1))))
//
//      val deployments: Option[Seq[Deployment]] = await(mongoWhatIsRunningWhereRepository.getForService("test"))
//
//      deployments.get.size shouldBe 4
//
//      deployments.get.map(_.version) shouldBe List("v4", "v3", "v2", "v1")
//
//    }
//  }


//  "add" should {
//    "be able to insert a new record and update it as well" in {
//      val now: LocalDateTime = LocalDateTime.now()
//      await(mongoWhatIsRunningWhereRepository.add(Deployment("test", "v", None, now, Some(1))))
//      val all = await(mongoWhatIsRunningWhereRepository.allServicedeployments)
//
//      all.values.flatten.size shouldBe 1
//      val savedDeployment: Deployment = all.values.flatten.loneElement
//
//      savedDeployment.name shouldBe "test"
//      savedDeployment.version shouldBe "v"
//      savedDeployment.creationDate shouldBe None
//      savedDeployment.productionDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) shouldBe now.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
//      savedDeployment.leadTime shouldBe None
//
//    }
//  }


}
