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

package uk.gov.hmrc.servicereleases

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, LoneElement, OptionValues}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class MongoReleasesRepositorySpec extends UnitSpec with LoneElement with MongoSpecSupport with ScalaFutures with OptionValues with BeforeAndAfterEach with WithFakeApplication {


  val mongoReleasesRepository = new MongoReleasesRepository(mongo)

  override def beforeEach() {
    await(mongoReleasesRepository.drop)
  }

  println(databaseName)

  "add" should {

    "be able to insert a new record and update it as well" in {
      val now: LocalDateTime = LocalDateTime.now()
      await(mongoReleasesRepository.add(Release("test", "v", None, now, 1)))
      val all = await(mongoReleasesRepository.getAll)

      all.values.flatten.size shouldBe 1
      val savedRelease: Release = all.values.flatten.loneElement

      savedRelease.name shouldBe "test"
      savedRelease.version shouldBe "v"
      savedRelease.creationDate shouldBe None
      savedRelease.productionDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) shouldBe now.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
      savedRelease.leadTime shouldBe None


    }
  }

  "update" should {
    "update already existing release" in {
      val now: LocalDateTime = LocalDateTime.now()
      await(mongoReleasesRepository.add(Release("test", "v", None, now, 1)))

      val all = await(mongoReleasesRepository.getAll)

      val savedRelease: Release = all.values.flatten.loneElement

      await(mongoReleasesRepository.update(savedRelease.copy(leadTime = Some(1))))

      val allUpdated = await(mongoReleasesRepository.getAll)
      allUpdated.size shouldBe 1
      val updatedRelease: Release = allUpdated.values.flatten.loneElement

      updatedRelease.name shouldBe savedRelease.name
      updatedRelease.version shouldBe savedRelease.version
      updatedRelease.creationDate shouldBe savedRelease.creationDate
      savedRelease.productionDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) shouldBe savedRelease.productionDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
      updatedRelease.leadTime shouldBe Some(1)
    }

  }

}
