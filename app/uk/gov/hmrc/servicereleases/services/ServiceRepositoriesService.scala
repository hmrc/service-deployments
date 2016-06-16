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

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class Repository(org: String, repoType: String)

trait ServiceRepositoriesService {
  def getAll(): Future[Map[String, Seq[Repository]]]
}

class DefaultServiceRepositoriesService(dataSource: ServiceDataSource) extends ServiceRepositoriesService {
  override def getAll(): Future[Map[String, Seq[Repository]]] =
    dataSource.getAll().map { services =>
      services.map { service =>
        service.name -> service.githubUrls.flatMap(u => toServiceRepo(service.name, u.name, u.url)) } toMap }

  private def toServiceRepo(service: String, repoType: String, repoUrl: String) =
    extractOrg(repoUrl).map { org => Repository(org, repoType) }

  val org = "^.*://.*(?<!/)/(.*)/.*(?<!/)$".r
  private def extractOrg(url: String) = url match {
    case org(o) => Some(o)
    case _ => None
  }
}
