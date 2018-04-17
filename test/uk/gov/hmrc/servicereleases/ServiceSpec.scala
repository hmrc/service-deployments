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

package uk.gov.hmrc.servicedeployments

import java.time.LocalDateTime

import org.scalatest.{FunSuite, Matchers, WordSpec}
import uk.gov.hmrc.servicedeployments.deployments.{Deployer, ServiceDeployment}

class ServiceSpec extends WordSpec with Matchers {

  val `31 July` = LocalDateTime.of(2016, 7, 31, 0, 0)
  val `23 August` = LocalDateTime.of(2016, 8, 23, 0, 0)
  val `26 August` = LocalDateTime.of(2016, 8, 26, 0, 0)
  val `27 August` = LocalDateTime.of(2016, 8, 27, 0, 0)
  val `28 August` = LocalDateTime.of(2016, 8, 28, 0, 0)
  val `29 August` = LocalDateTime.of(2016, 8, 29, 0, 0)
  val `30 August` = LocalDateTime.of(2016, 8, 30, 0, 0)


  "deploymentInterval" should {

    "correct deployment interval for a given version" in {

      val deploymemts = Seq(
        ServiceDeployment("1.0.0", `28 August`),
        ServiceDeployment("0.1.0", `26 August`),
        ServiceDeployment("0.0.1", `23 August`),
        ServiceDeployment("2.0.0",`30 August`)
      )

      val service = Service("name", Seq(), deployments = deploymemts, Seq())

      service.deploymentInterval("0.0.1") shouldBe None
      service.deploymentInterval("0.1.0") shouldBe Some(3)
      service.deploymentInterval("1.0.0") shouldBe Some(2)
      service.deploymentInterval("2.0.0") shouldBe Some(2)

    }
    "return deployment interval for a old deployment not in deployments but known to us" in {

      val deploymemts = Seq(
        ServiceDeployment("0.1.0", `26 August`),
        ServiceDeployment("0.0.1", `23 August`),
        ServiceDeployment("2.0.0",`30 August`)
      )

      val service = Service("name", Seq(), deployments = deploymemts, Seq(Deployment("name", "1.0.0", None, `31 July`, Some(1), None)))

      service.deploymentInterval("0.0.1") should be(Some(23))

    }


  }


  "deploymentsRequiringUpdates" should {

    "return if deployment is known but is re-deployed" in {
      val oldDeploymentDate: LocalDateTime = LocalDateTime.now().minusDays(60)

      val deploymemts = Seq(
        ServiceDeployment("0.0.1", `23 August`),
        ServiceDeployment("1.0.0", oldDeploymentDate, Seq(Deployer(name = "abc.xyz", `29 August`)))
      )

      val service = Service("name", Seq(), deployments = deploymemts, Seq(Deployment("name", "1.0.0", None, oldDeploymentDate, Some(1), None)))

      service.deploymentsRequiringUpdates should contain(ServiceDeployment("1.0.0", oldDeploymentDate, Seq(Deployer(name = "abc.xyz", `29 August`))))

    }

    "not contain duplicate deployments if already known and known deployment should take prefrence" in {

      val deploymemts = Seq(
        ServiceDeployment("0.1.0", `26 August`),
        ServiceDeployment("0.0.1", `23 August`),
        ServiceDeployment("2.0.0",`30 August`)
      )

      val oldDeploymentDate: LocalDateTime = LocalDateTime.now().minusDays(60)
      val service = Service("name", Seq(), deployments = deploymemts, Seq(Deployment("name", "0.1.0", None, oldDeploymentDate, Some(1), None)))

      service.deploymentsRequiringUpdates.size shouldBe 2

      service.deploymentsRequiringUpdates should be(
        Seq(ServiceDeployment("0.0.1", `23 August`), ServiceDeployment("2.0.0",`30 August`))
      )

    }

  }


}
