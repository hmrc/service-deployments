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

package uk.gov.hmrc.servicedeployments.services

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.servicedeployments.FutureHelpers
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.matching.Regex

case class Repository(org: String)

@Singleton
class ServiceRepositoriesService @Inject()(
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  futureHelpers: FutureHelpers) {

  def getAll: Future[Map[String, Repository]] =
    teamsAndRepositoriesConnector.getAll().map { services =>
      services.map { service =>
        service.name -> toServiceRepo(service.name, service.githubUrl.url).get
      }.toMap
    } map { result =>
      Logger.info(s"Found ${result.count(_ => true)} services")
      result
    }

  private def toServiceRepo(service: String, repoUrl: String) =
    extractOrg(repoUrl).map(Repository.apply)

  val org: Regex = "^.*://.*(?<!/)/(.*)/.*(?<!/)$".r

  private def extractOrg(url: String) = url match {
    case org(o) => Some(o)
    case _      => None
  }
}
