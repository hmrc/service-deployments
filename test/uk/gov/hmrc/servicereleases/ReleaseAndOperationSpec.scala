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

import java.time.{LocalDate, LocalDateTime}

import org.scalatest.{LoneElement, Matchers, WordSpec}
import play.api.Logger
import uk.gov.hmrc.servicereleases.Release
import uk.gov.hmrc.servicereleases.ReleaseOperation.Add
import uk.gov.hmrc.servicereleases.deployments.ServiceDeployment

class ReleaseAndOperationSpec extends WordSpec with Matchers with LoneElement{

  val `26 August` = LocalDateTime.of(2016, 8, 26, 0, 0)
  val `27 August` = LocalDateTime.of(2016, 8, 27, 0, 0)
  val `28 August` = LocalDateTime.of(2016, 8, 28, 0, 0)
  val `29 August` = LocalDateTime.of(2016, 8, 29, 0, 0)
  val `30 August` = LocalDateTime.of(2016, 8, 30, 0, 0)


  "get" should {
    "return releases with operation as add for new releases" in {


      val tagDates = Map(
        "1.0.0" -> `26 August`,
        "2.0.0" -> `28 August`
      )

      val deployments = Seq(
        ServiceDeployment("1.0.0", `28 August`),
        ServiceDeployment("2.0.0", `30 August`)
      )

      val knownReleases = Seq(
        Release("sName", "1.0.0", Some(`26 August`), `28 August`, None, Some(2))
      )

      val service: Service = Service("sName", Seq(), deployments, knownReleases)

      val releaseUpdates: Seq[(ReleaseOperation.Value, Release)] = new ReleaseAndOperation(service, tagDates).get

      releaseUpdates.loneElement._1 shouldBe Add   //(Add,Release("sName", "2.0.0", Some(`28 August`), `30 August`, None, Some(2)))
      releaseUpdates.loneElement._2.version shouldBe "2.0.0" //(Add,Release("sName", "2.0.0", Some(`28 August`), `30 August`, None, Some(2)))

    }


    "return releases with operation as update for new releases" in {


      val tagDates = Map(
        "1.0.0" -> `26 August`,
        "2.0.0" -> `28 August`
      )

      val deployments = Seq(
        ServiceDeployment("1.0.0", `28 August`),
        ServiceDeployment("2.0.0", `30 August`)
      )

      val knownReleases = Seq(
        Release("sName", "1.0.0", Some(`26 August`), `28 August`, None, Some(2))
      )

      val service: Service = Service("sName", Seq(), deployments, knownReleases)

      val releaseUpdates: Seq[(ReleaseOperation.Value, Release)] = new ReleaseAndOperation(service, tagDates).get

      releaseUpdates.loneElement._1 shouldBe Add   //(Add,Release("sName", "2.0.0", Some(`28 August`), `30 August`, None, Some(2)))
      releaseUpdates.loneElement._2.version shouldBe "2.0.0" //(Add,Release("sName", "2.0.0", Some(`28 August`), `30 August`, None, Some(2)))

    }

  }

}
