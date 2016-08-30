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

import org.scalatest.{Matchers, WordSpec, FunSuite}
import uk.gov.hmrc.servicereleases.deployments.ServiceDeployment

class ServiceSpec extends WordSpec with Matchers {

  val `23 August` = LocalDateTime.of(2016, 8, 23, 0, 0)
  val `26 August` = LocalDateTime.of(2016, 8, 26, 0, 0)
  val `27 August` = LocalDateTime.of(2016, 8, 27, 0, 0)
  val `28 August` = LocalDateTime.of(2016, 8, 28, 0, 0)
  val `29 August` = LocalDateTime.of(2016, 8, 29, 0, 0)
  val `30 August` = LocalDateTime.of(2016, 8, 30, 0, 0)


  "releaseInterval" should {

    "correct release interval for a given version" in {

      val deploymemts = Seq(
        ServiceDeployment("1.0.0", `28 August`),
        ServiceDeployment("0.1.0", `26 August`),
        ServiceDeployment("0.0.1", `23 August`),
        ServiceDeployment("2.0.0",`30 August`)
      )

      val service = Service("name", Seq(), deployments = deploymemts, Seq())

      service.releaseInterval("0.0.1") shouldBe None
      service.releaseInterval("0.1.0") shouldBe Some(3)
      service.releaseInterval("1.0.0") shouldBe Some(2)
      service.releaseInterval("2.0.0") shouldBe Some(2)

    }
  }


}
