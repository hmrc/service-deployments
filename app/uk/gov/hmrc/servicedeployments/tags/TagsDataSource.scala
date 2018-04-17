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

package uk.gov.hmrc.servicedeployments.tags

import java.time.{LocalDateTime, ZoneId}
import javax.inject.Singleton

import uk.gov.hmrc.servicedeployments.{GithubApiClientEnterprise, GithubApiClientOpen}

import javax.inject.Inject

import uk.gov.hmrc.BlockingIOExecutionContext
import uk.gov.hmrc.gitclient.{GitClient, GitTag}
import uk.gov.hmrc.githubclient.{GhRepoRelease, GithubApiClient}
import uk.gov.hmrc.servicedeployments.FutureHelpers

import scala.concurrent.Future

case class Tag(version: String, createdAt: LocalDateTime)

object Tag {
  implicit def ghRepoDeploymentsToServiceDeploymentTags(gr: List[GhRepoRelease]): List[Tag] = gr.map(Tag.apply)

  implicit def gitTagsToServiceDeploymentTags(gt: List[GitTag]): List[Tag] = gt.map(Tag.apply)

  def apply(gt: GitTag): Tag = Tag(getVersionNumber(gt.name), gt.createdAt.get.toLocalDateTime)

  def apply(ghr: GhRepoRelease): Tag =
    Tag(getVersionNumber(ghr.tagName), ghr.createdAt.toInstant.atZone(ZoneId.systemDefault()).toLocalDateTime)

  private val versionNumber = "(?:(\\d+)\\.)?(?:(\\d+)\\.)?(\\*|\\d+)$".r

  private def getVersionNumber(tag: String): String = versionNumber.findFirstIn(tag).getOrElse(tag)
}

@Singleton
class GitConnectorOpen @Inject()(futureHelpers: FutureHelpers, gitHubClientOpen: GithubApiClientOpen, identifier: String) {

  import BlockingIOExecutionContext.executionContext
  import futureHelpers._

  def get(organisation: String, repoName: String): Future[List[Tag]] =
    withTimerAndCounter(s"git.api.$identifier") {
      gitHubClientOpen.getReleases(organisation, repoName).map(identity(_))
    }
}
