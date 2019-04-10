/*
 * Copyright 2019 HM Revenue & Customs
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
import java.time.{LocalDateTime, ZonedDateTime}

import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.BuiltInComponentsFromContext
import play.api.routing.Router
import play.api.test.WsTestClient
import play.filters.HttpFiltersComponents
import uk.gov.hmrc.HttpClient
import uk.gov.hmrc.servicedeployments.{ServiceDeploymentsConfig, TestServiceDependenciesConfig}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ArtifactoryConnectorSpec extends WordSpec with Matchers with MockitoSugar with WsTestClient {


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

  "ArtifactoryConnectory" should {

    "read the version data from artifactory" in {
      withArtifactoryConnector(None, { conn =>
        val resp = conn.findVersion("test-artifact", "1.2.3")
        val result = Await.result(resp, Duration(1, "seconds"))
        assert(result.version == "1.2.3")
        assert(result.createdAt == LocalDateTime.of(2018,7,16,16,19,36))
      })
    }

    "send artifactory API key in header when configured" in {

      withArtifactoryConnector(Some("validtoken"), { conn =>
        val resp = conn.findVersion("test-artifact", "1.2.3")
        val result = Await.result(resp, Duration(1, "seconds"))
        assert(result.version == "1.2.3")
        assert(result.createdAt == LocalDateTime.of(2018,7,16,16,19,36))
      })
    }

    "fail gracefully when API token is required but invalid" in {
      withArtifactoryConnector(Some("invalidtoken"), { conn =>
        val resp = conn.findVersion("test-artifact", "1.2.3")
        val result = Await.ready(resp, Duration(1, "seconds"))
        assert(result.value.get.isFailure)
      })
    }
  }


  import play.api.mvc._
  import play.api.routing.sird._
  import play.api.test._
  import play.core.server.Server

  def withArtifactoryConnector[T](token: Option[String], block: ArtifactoryConnector => T): T = {

    Server.withApplicationFromContext() { context =>
      new BuiltInComponentsFromContext(context) with HttpFiltersComponents{
        override def router: Router = Router.from {
          case GET(_) =>
            Action { req =>
            req.headers.get("X-JFrog-Art-Api") match {
              case None => Results.Ok(json)
              case Some("validtoken") => Results.Ok(json)
              case Some(_) => Results.Forbidden
            }
          }
        }
      }.application
    } { implicit port =>
      WsTestClient.withClient { client =>
      {
        val config: ServiceDeploymentsConfig = new TestServiceDependenciesConfig() {
          override lazy val artifactoryApiKey: Option[String] = token
          override lazy val artifactoryBase: String = s"http://localhost:$port/artifactory"
        }
        block(new ArtifactoryConnector(new HttpClient(client), config))
      }

      }
    }
  }


  val json =
    """
      |{
      |  "repo" : "test-releases-local",
      |  "path" : "/uk/gov/hmrc/test-artifact_2.11/1.2.3",
      |  "created" : "2018-07-16T16:19:36.0Z",
      |  "createdBy" : "test",
      |  "lastModified" : "2018-07-16T16:19:36.0Z",
      |  "modifiedBy" : "admin",
      |  "lastUpdated" : "2018-07-16T16:19:36.0Z",
      |  "children" : [ {
      |    "uri" : "/test-artifact_2.11-41.2.3.jar",
      |    "folder" : false
      |  }, {
      |    "uri" : "/test-artifact_2.11-1.2.3.pom",
      |    "folder" : false
      |  }, {
      |    "uri" : "/test-artifact_2.11-1.2.3.tgz",
      |    "folder" : false
      |  } ],
      |  "uri" : "https://localhost/artifactory/api/storage/hmrc-releases-local/uk/gov/hmrc/test-artifact_2.11/1.2.3"
      |}
    """.stripMargin
}
