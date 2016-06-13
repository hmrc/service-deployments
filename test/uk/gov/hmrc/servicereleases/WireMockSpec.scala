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

import java.net.{ServerSocket, URL}

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{MappingBuilder, RequestPatternBuilder, ResponseDefinitionBuilder, WireMock}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import com.github.tomakehurst.wiremock.http.RequestMethod
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, WordSpec}
import play.api.libs.json.Json

import scala.collection.JavaConversions._
import scala.util.Try

case class HttpRequest(method: RequestMethod, url: String, body: Option[String]) {
  {
    body.foreach { b => Json.parse(b) }
  }

  def req: RequestPatternBuilder = {
    val builder = new RequestPatternBuilder(method, urlEqualTo(url))
    body.map { b =>
      builder.withRequestBody(equalToJson(b))
    }.getOrElse(builder)
  }
}

trait WireMockSpec extends WordSpec with BeforeAndAfterAll with BeforeAndAfterEach{
  val host: String = "localhost"
  val port: Int = PortTester.findPort()

  val endpointMock = new WireMock(host, port)
  val endpointMockUrl = s"http://$host:$port"
  val endpointServer: WireMockServer = new WireMockServer(wireMockConfig().port(port))

  def startWireMock() = endpointServer.start()

  def stopWireMock() = endpointServer.stop()

  override def beforeEach(): Unit = {
    endpointMock.resetMappings()
    endpointMock.resetScenarios()
  }

  override def afterAll(): Unit = {
    endpointServer.stop()
  }

  override def beforeAll(): Unit = {
    endpointServer.start()
  }

  def printMappings(): Unit = {
    endpointMock.allStubMappings().getMappings.toList.foreach { s =>
      println(s)
    }
  }

  def givenRequestExpects(
                           method: RequestMethod,
                           url: String,
                           extraHeaders: Map[String, String] = Map(),
                           willRespondWith: (Int, Option[String]), headers : List[(String, String)] = List()): Unit = {

    val builder = new MappingBuilder(method, urlPathEqualTo(new URL(url).getPath))

    headers.foreach(x=> builder.withHeader(x._1, equalTo(x._2)))



    val response: ResponseDefinitionBuilder = new ResponseDefinitionBuilder()
      .withStatus(willRespondWith._1)

    val resp = willRespondWith._2.map { b =>
      response.withBody(b)
    }.getOrElse(response)

    builder.willReturn(resp)

    endpointMock.register(builder)
  }

  def assertRequest(
                     method: RequestMethod,
                     url: String,
                     extraHeaders: Map[String, String] = Map(),
                     jsonBody: Option[String]): Unit = {
    val builder = new RequestPatternBuilder(method, urlPathEqualTo(new URL(url).getPath))
    extraHeaders.foreach { case (k, v) =>
      builder.withHeader(k, equalTo(v))
    }

    jsonBody.map { b =>
      builder.withRequestBody(equalToJson(b))
    }.getOrElse(builder)
    endpointMock.verifyThat(builder)
  }

  def assertRequest(req: HttpRequest): Unit = {
    endpointMock.verifyThat(req.req)
  }
}

object PortTester {

  def findPort(excluded: Int*): Int = {
    (6001 to 7000).find(port => !excluded.contains(port) && isFree(port)).getOrElse(throw new Exception("No free port"))
  }

  private def isFree(port: Int): Boolean = {
    val triedSocket = Try {
      val serverSocket = new ServerSocket(port)
      Try(serverSocket.close())
      serverSocket
    }
    triedSocket.isSuccess
  }
}
