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


  "getAll" should {
    "return all the releases in descending order of productionDate" in {

      val now: LocalDateTime = LocalDateTime.now()

      await(mongoReleasesRepository.add(Release("test2", "v2", None, productionDate = now.minusDays(6))))
      await(mongoReleasesRepository.add(Release("test3", "v3", None, productionDate = now.minusDays(5))))
      await(mongoReleasesRepository.add(Release("test1", "v1", None, productionDate = now.minusDays(10))))
      await(mongoReleasesRepository.add(Release("test4", "v4", None, productionDate = now.minusDays(2))))
      await(mongoReleasesRepository.add(Release("test5", "vSomeOther1", None, now.minusDays(2), Some(1))))
      await(mongoReleasesRepository.add(Release("test5", "vSomeOther2", None, now, Some(1))))

      val result: Seq[Release] = await(mongoReleasesRepository.getAllReleases)

      result.map(x => (x.name, x.version)) shouldBe Seq(
        ("test5", "vSomeOther2"),
        ("test4", "v4"),
        ("test5", "vSomeOther1"),
        ("test3", "v3"),
        ("test2", "v2"),
        ("test1", "v1")
      )

    }
  }

  "getForService" should {
    "return releases for a service sorted in descending order of productionDate" in {
      val now: LocalDateTime = LocalDateTime.now()

      await(mongoReleasesRepository.add(Release("randomService", "vSomeOther1", None, now, Some(1))))
      await(mongoReleasesRepository.add(Release("test", "v1", None, productionDate = now.minusDays(10), interval = Some(1))))
      await(mongoReleasesRepository.add(Release("test", "v2", None, productionDate = now.minusDays(6), interval = Some(1))))
      await(mongoReleasesRepository.add(Release("test", "v3", None, productionDate = now.minusDays(5), Some(1))))
      await(mongoReleasesRepository.add(Release("test", "v4", None, productionDate = now.minusDays(2), Some(1))))

      val releases: Option[Seq[Release]] = await(mongoReleasesRepository.getForService("test"))

      releases.get.size shouldBe 4

      releases.get.map(_.version) shouldBe List("v4", "v3", "v2", "v1")

    }
  }


  "add" should {
    "be able to insert a new record and update it as well" in {
      val now: LocalDateTime = LocalDateTime.now()
      await(mongoReleasesRepository.add(Release("test", "v", None, now, Some(1))))
      val all = await(mongoReleasesRepository.allServiceReleases)

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
      await(mongoReleasesRepository.add(Release("test", "v", None, now)))

      val all = await(mongoReleasesRepository.allServiceReleases)

      val savedRelease: Release = all.values.flatten.loneElement

      await(mongoReleasesRepository.update(savedRelease.copy(leadTime = Some(1))))

      val allUpdated = await(mongoReleasesRepository.allServiceReleases)
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
