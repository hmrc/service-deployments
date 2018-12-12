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

package uk.gov.hmrc.servicedeployments.connectors
import java.time.ZonedDateTime

import org.scalatest.{Matchers, WordSpec}

class ArtifactoryConnectorSpec extends WordSpec with Matchers {


  "Date Format" should {

    "parse 2018-08-20T17:01:04.787Z" in {

      val date = "2018-08-20T17:01:04.787Z"
      val res  = ZonedDateTime.parse(date)
      assert(res.getYear == 2018)
    }

  }

  "artifactFromPath" should {
    import ArtifactoryConnector.artifactFromPath

    val path = "/uk/gov/hmrc/mongo-lock_2.11"

    "remove the scala version from the end of the path" in {
      artifactFromPath(path).endsWith("_2.11") shouldBe false
    }

    "select the last item in the path" in {
      artifactFromPath(path) shouldBe "mongo-lock"
    }

    "return the input unmodified if its not parsable" in {
      artifactFromPath("a-non-standard-path") shouldBe "a-non-standard-path"
    }
  }


  "versionFromPath" should {
    import ArtifactoryConnector.versionFromPath

    "extract 4.185.0 from path" in {
      val path = "uk/gov/hmrc/catalogue-frontend_2.11/4.185.0"
      versionFromPath(path) shouldBe Some("4.185.0")
    }

    "extract nothing from a path without version number" in {
      val path = "uk/gov/hmrc/catalogue-frontend_2.11"
      versionFromPath(path) shouldBe None
    }

  }

}
