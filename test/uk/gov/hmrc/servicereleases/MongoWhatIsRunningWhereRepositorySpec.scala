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
import org.scalatest._
import org.scalatestplus.play.OneAppPerTest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.servicedeployments.{MongoWhatIsRunningWhereRepository, WhatIsRunningWhereModel}
import uk.gov.hmrc.servicedeployments.deployments.{Environment, WhatIsRunningWhere}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


class MongoWhatIsRunningWhereRepositorySpec extends FunSpec with Matchers with LoneElement with MongoSpecSupport with ScalaFutures with OptionValues with BeforeAndAfterEach with OneAppPerTest {


  def await[A](future: Future[A]) = Await.result(future, 5 seconds)


  val mongoWhatIsRunningWhereRepository = new MongoWhatIsRunningWhereRepository(mongo)

  override def beforeEach() {
    await(mongoWhatIsRunningWhereRepository.drop)
  }

  describe("update") {
    it("should update already existing items") {

      val whatIsRunningWhere = WhatIsRunningWhere("app-1", Set(Environment("qa", "qa") , Environment("production", "production")))
      await(mongoWhatIsRunningWhereRepository.update(whatIsRunningWhere))

      val all = await(mongoWhatIsRunningWhereRepository.findAll())

      val savedWhatIsRunningWhere: WhatIsRunningWhereModel = all.loneElement

      await(mongoWhatIsRunningWhereRepository.update(whatIsRunningWhere.copy(environments= Set(Environment("staging", "staging")))))

      val allUpdated = await(mongoWhatIsRunningWhereRepository.getAll)
      allUpdated.size shouldBe 1
      val updatedWhatIsRunningWhere: WhatIsRunningWhereModel = allUpdated.loneElement

      updatedWhatIsRunningWhere.applicationName shouldBe whatIsRunningWhere.applicationName
      updatedWhatIsRunningWhere.environments shouldBe Set(Environment("staging", "staging"))

    }

  }


  describe("getAll") {
      import uk.gov.hmrc.mongo.json.ReactiveMongoFormats._
      import scala.concurrent.ExecutionContext.Implicits.global
      import reactivemongo.json._
      import play.api.libs.json._

      it("return all the whatIsRunningWhere records") {

        await(mongoWhatIsRunningWhereRepository.collection.insert(Json.obj(
          "applicationName" -> "app-123" ,
          "environments" -> Set(Environment("qa", "qa"), Environment("production", "production"))
        )))

        val whatIsRunningWheres: Seq[WhatIsRunningWhereModel] = await(mongoWhatIsRunningWhereRepository.getAll)

        whatIsRunningWheres.size shouldBe 1
        whatIsRunningWheres.head.applicationName shouldBe "app-123"
        whatIsRunningWheres.head.environments shouldBe Set(Environment("qa", "qa"), Environment("production", "production"))

      }
    }

    describe("allGroupedByName") {
      import uk.gov.hmrc.mongo.json.ReactiveMongoFormats._
      import scala.concurrent.ExecutionContext.Implicits.global
      import reactivemongo.json._
      import play.api.libs.json._

      it("should return all the whatIsRunningWhere grouped by application name") {

        await(mongoWhatIsRunningWhereRepository.collection.insert(Json.obj(
          "applicationName" -> "app-1",
          "environments" -> Set(Environment("qa", "qa"), Environment("production", "production"))
        )))
        await(mongoWhatIsRunningWhereRepository.collection.insert(Json.obj(
          "applicationName" -> "app-2",
          "environments" -> Set(Environment("qa", "qa"))
        )))

        val whatIsRunningWheres: Map[String, Seq[WhatIsRunningWhereModel]] = await(mongoWhatIsRunningWhereRepository.allGroupedByName)

        whatIsRunningWheres.size shouldBe 2
        whatIsRunningWheres.keys should contain theSameElementsAs Seq("app-1", "app-2")
        whatIsRunningWheres("app-1").size shouldBe 1
        whatIsRunningWheres("app-1").head.environments should contain theSameElementsAs Set(Environment("qa", "qa"), Environment("production", "production"))

        whatIsRunningWheres("app-2").size shouldBe 1
        whatIsRunningWheres("app-2").head.environments should contain theSameElementsAs Set(Environment("qa", "qa"))

      }
    }

  describe("getForApplication" ) {
      import uk.gov.hmrc.mongo.json.ReactiveMongoFormats._
      import scala.concurrent.ExecutionContext.Implicits.global
      import reactivemongo.json._
      import play.api.libs.json._

      it("return the whatIsRunningWhere for the given application name" ) {

      await(mongoWhatIsRunningWhereRepository.collection.insert(Json.obj(
        "applicationName" -> "app-1" ,
        "environments" -> Set(Environment("qa", "qa"), Environment("production", "production"))
      )))
      await(mongoWhatIsRunningWhereRepository.collection.insert(Json.obj(
        "applicationName" -> "app-2" ,
        "environments" -> Set(Environment("qa", "qa"))
      )))

      val whatIsRunningWheres: Option[Seq[WhatIsRunningWhereModel]] = await(mongoWhatIsRunningWhereRepository.getForApplication("app-1"))

      whatIsRunningWheres.value.size shouldBe 1
      whatIsRunningWheres.value.head.applicationName shouldBe "app-1"
      whatIsRunningWheres.value.head.environments shouldBe Set(Environment("qa", "qa"), Environment("production", "production"))

    }
  }

  describe("clearAllData") {
    import uk.gov.hmrc.mongo.json.ReactiveMongoFormats._
    import scala.concurrent.ExecutionContext.Implicits.global
    import reactivemongo.json._
    import play.api.libs.json._

    it("remove all the whatIsRunningWhere records") {

      await(mongoWhatIsRunningWhereRepository.collection.insert(Json.obj(
        "applicationName" -> "app-1",
        "environments" -> Seq("qa", "production")
      )))
      await(mongoWhatIsRunningWhereRepository.collection.insert(Json.obj(
        "applicationName" -> "app-2",
        "environments" -> Seq("qa")
      )))

      await(mongoWhatIsRunningWhereRepository.clearAllData)

      val whatIsRunningWheres = await(mongoWhatIsRunningWhereRepository.getAll)

      whatIsRunningWheres.size shouldBe 0

    }
  }


}
