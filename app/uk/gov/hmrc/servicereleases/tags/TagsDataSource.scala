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

package uk.gov.hmrc.servicereleases.tags

import java.time.{LocalDateTime, ZoneId}

import play.api.Logger
import uk.gov.hmrc.{BlockingIOExecutionContext, FuturesCache}
import uk.gov.hmrc.gitclient.{GitClient, GitTag}
import uk.gov.hmrc.githubclient.{GhRepoRelease, GithubApiClient}

import scala.concurrent.Future
import scala.concurrent.duration._

case class Tag(version: String, createdAt: LocalDateTime)

object Tag {
  implicit def ghRepoReleasesToServiceReleaseTags(gr: List[GhRepoRelease]): List[Tag] = gr.map(Tag.apply)
  implicit def gitTagsToServiceReleaseTags(gt: List[GitTag]): List[Tag] = gt.map(Tag.apply)

  def apply(gt: GitTag): Tag = Tag(getVersionNumber(gt.name), gt.createdAt.get.toLocalDateTime)
  def apply(ghr: GhRepoRelease): Tag =
    Tag(getVersionNumber(ghr.tagName), ghr.createdAt.toInstant.atZone(ZoneId.systemDefault()).toLocalDateTime)

  private val versionNumber = "(?:(\\d+)\\.)?(?:(\\d+)\\.)?(\\*|\\d+)$".r
  private def getVersionNumber(tag: String): String = versionNumber.findFirstIn(tag).getOrElse(tag)
}

trait TagsDataSource {
  def get(organisation: String, repoName: String): Future[List[Tag]]
}

class CachedTagsDataSource(tagsDataSource: TagsDataSource) extends TagsDataSource with FuturesCache[(String, String), List[Tag]] {
  override def refreshTimeInMillis: Duration = 3 hours
  override protected def cacheLoader: ((String, String)) => Future[List[Tag]] =
    key => tagsDataSource.get(key._1, key._2)

  def get(organisation: String, repoName: String) = cache.getUnchecked((organisation, repoName))
}

class GitHubConnector(gitHubClient: GithubApiClient) extends TagsDataSource {
  import BlockingIOExecutionContext.executionContext

  def get(organisation: String, repoName: String) =
    gitHubClient.getReleases(organisation, repoName).map(identity(_))
}

class GitConnector(gitClient: GitClient, githubApiClient: GithubApiClient) extends TagsDataSource {
  import BlockingIOExecutionContext.executionContext

  def get(organisation: String, repoName: String) =
    gitClient.getGitRepoTags(repoName, organisation).flatMap { x =>
        val (withCreatedAt, withoutCreatedAt) = x.partition(_.createdAt.isDefined)
        val serviceRelease: List[Tag] = withCreatedAt

        tagsWithReleaseDate(withoutCreatedAt, organisation, repoName).map(serviceRelease ++ _)}

  private def tagsWithReleaseDate(gitTags: List[GitTag], organisation: String, repoName: String): Future[List[Tag]] =
    if (gitTags.nonEmpty) {
      Logger.warn(s"$repoName invalid git Tags total : ${gitTags.size} getting git releases")

      for (rs <- githubApiClient.getReleases(organisation, repoName))
      yield {
        val releaseTags = gitTags.flatMap { gitTag => rs.find(_.tagName == gitTag.name).map(Tag.apply) }

        Logger.info(s"$repoName tags from git releases total : ${releaseTags.size}")
        releaseTags
      }

    } else Future.successful(Nil)
}




