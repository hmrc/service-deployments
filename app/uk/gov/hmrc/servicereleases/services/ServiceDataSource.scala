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

package uk.gov.hmrc.servicereleases.services

import play.api.http.HeaderNames
import play.api.libs.json.Json
import uk.gov.hmrc.HttpClient._

import scala.concurrent.Future

case class GithubUrl(name: String, url: String)
case class Service(name: String, githubUrls: List[GithubUrl])

trait ServiceDataSource {
  def getAll(): Future[List[Service]]
}

class CatalogueConnector(apiBase: String) extends ServiceDataSource {
  implicit val urlReads = Json.reads[GithubUrl]
  implicit val reads = Json.reads[Service]

  override def getAll() = get[List[Service]](s"$apiBase/services", HeaderNames.ACCEPT -> "application/vnd.servicedetails.hal+json")
}
