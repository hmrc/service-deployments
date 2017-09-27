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

//!@
///*
// * Copyright 2017 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.servicedeployments
//
//import java.time.format.DateTimeFormatter
//import java.time.{LocalDateTime, ZoneOffset}
//
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatest.{BeforeAndAfterEach, LoneElement, OptionValues}
//import org.scalatestplus.play.OneAppPerTest
//import uk.gov.hmrc.mongo.MongoSpecSupport
//import uk.gov.hmrc.play.test.UnitSpec
//import uk.gov.hmrc.servicedeployments.deployments.Deployer
//
//import scala.concurrent.ExecutionContext.Implicits.global
//
//class MongoDeploymentsRepositorySpec extends UnitSpec with LoneElement with MongoSpecSupport with ScalaFutures with OptionValues with BeforeAndAfterEach with OneAppPerTest {
//
//
//  val mongoDeploymentsRepository = new DeploymentsRepository(mongo)
//
//  override def beforeEach() {
//    await(mongoDeploymentsRepository.drop)
//  }
//
//
//  "getAll" should {
//    "return all the deployments when already saved deployments do not have 'deployers' " in {
//
//
//      import play.api.libs.json._
//      import reactivemongo.play.json._
//
//      import scala.concurrent.ExecutionContext.Implicits.global
//
//      val now = LocalDateTime.now()
//      implicit val dateWriteFormat =  Deployment.localDateTimeToEpochSecondsWrites
//
//      await(mongoDeploymentsRepository.collection.insert(Json.obj(
//        "name" -> "nisp" ,
//        "version" -> "5.4.2" ,
//        "productionDate" -> now.toEpochSecond(ZoneOffset.UTC) ,
//        "interval" -> 1
//      )))
//
//      val deployments: Seq[Deployment] = await(mongoDeploymentsRepository.getAllDeployments)
//
//      deployments.size shouldBe 1
//      deployments.head.creationDate shouldBe None
//      deployments.head.productionDate.toLocalDate shouldBe now.toLocalDate
//      deployments.head.productionDate.toLocalTime.getMinute shouldBe now.toLocalTime.getMinute
//      deployments.head.productionDate.toLocalTime.getSecond shouldBe now.toLocalTime.getSecond
//      deployments.head.name shouldBe "nisp"
//      deployments.head.version shouldBe "5.4.2"
//      deployments.head.interval.get shouldBe 1
//      deployments.head.leadTime shouldBe None
//      deployments.head.deployers shouldBe Seq.empty
//
//
//    }
//
//    "return all the deployments in descending order of productionDate" in {
//
//      val now: LocalDateTime = LocalDateTime.now()
//
//      await(mongoDeploymentsRepository.add(Deployment("test2", "v2", None, productionDate = now.minusDays(6))))
//      await(mongoDeploymentsRepository.add(Deployment("test3", "v3", None, productionDate = now.minusDays(5))))
//      await(mongoDeploymentsRepository.add(Deployment("test1", "v1", None, productionDate = now.minusDays(10))))
//      await(mongoDeploymentsRepository.add(Deployment("test4", "v4", None, productionDate = now.minusDays(2))))
//      await(mongoDeploymentsRepository.add(Deployment("test5", "vSomeOther1", None, now.minusDays(2), Some(1))))
//      await(mongoDeploymentsRepository.add(Deployment("test5", "vSomeOther2", None, now, Some(1), None, Seq(Deployer("xyz.abc", now)), None)))
//
//      val result: Seq[Deployment] = await(mongoDeploymentsRepository.getAllDeployments)
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
//  }
//
//  "getForService" should {
//    "return deployments for a service sorted in descending order of productionDate" in {
//      val now: LocalDateTime = LocalDateTime.now()
//
//      await(mongoDeploymentsRepository.add(Deployment("randomService", "vSomeOther1", None, now, Some(1))))
//      await(mongoDeploymentsRepository.add(Deployment("test", "v1", None, productionDate = now.minusDays(10), interval = Some(1))))
//      await(mongoDeploymentsRepository.add(Deployment("test", "v2", None, productionDate = now.minusDays(6), interval = Some(1))))
//      await(mongoDeploymentsRepository.add(Deployment("test", "v3", None, productionDate = now.minusDays(5), Some(1))))
//      await(mongoDeploymentsRepository.add(Deployment("test", "v4", None, productionDate = now.minusDays(2), Some(1))))
//
//      val deployments: Option[Seq[Deployment]] = await(mongoDeploymentsRepository.getForService("test"))
//
//      deployments.get.size shouldBe 4
//
//      deployments.get.map(_.version) shouldBe List("v4", "v3", "v2", "v1")
//
//    }
//  }
//
//
//  "add" should {
//    "be able to insert a new record and update it as well" in {
//      val now: LocalDateTime = LocalDateTime.now()
//      await(mongoDeploymentsRepository.add(Deployment("test", "v", None, now, Some(1))))
//      val all = await(mongoDeploymentsRepository.allServicedeployments)
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
//
//  "update" should {
//    "update already existing deployment" in {
//      val now: LocalDateTime = LocalDateTime.now()
//      await(mongoDeploymentsRepository.add(Deployment("test", "v", None, now)))
//
//      val all = await(mongoDeploymentsRepository.allServicedeployments)
//
//      val savedDeployment: Deployment = all.values.flatten.loneElement
//
//      await(mongoDeploymentsRepository.update(savedDeployment.copy(leadTime = Some(1))))
//
//      val allUpdated = await(mongoDeploymentsRepository.allServicedeployments)
//      allUpdated.size shouldBe 1
//      val updatedDeployment: Deployment = allUpdated.values.flatten.loneElement
//
//      updatedDeployment.name shouldBe savedDeployment.name
//      updatedDeployment.version shouldBe savedDeployment.version
//      updatedDeployment.creationDate shouldBe savedDeployment.creationDate
//      savedDeployment.productionDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) shouldBe savedDeployment.productionDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
//      updatedDeployment.leadTime shouldBe Some(1)
//    }
//
//  }
//
//}
