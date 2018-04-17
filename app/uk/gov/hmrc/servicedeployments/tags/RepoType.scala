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

object RepoType {
  case object Enterprise extends RepoType
  case object Open extends RepoType

  def from(st: String): RepoType = {
    st match {
      case "github-enterprise" => throw new RuntimeException(s"We no longer support GitHub enterprise")
      case "github-com" => Open
      case _ => throw new RuntimeException(s"Unknown repo type $st")
    }
  }
}

sealed trait RepoType
