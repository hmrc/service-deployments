/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.servicedeployments.deployments.{DeploymentsDataSource, ServiceDeploymentInformation}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class WhatIsRunningWhereUpdateService @Inject()(
  whatIsRunningWhereDataSource: DeploymentsDataSource,
  repository: WhatIsRunningWhereRepository) {

  def updateModel(): Future[List[Boolean]] =
    for {
      whatsRunningWhere <- whatIsRunningWhereDataSource.whatIsRunningWhere
      success           <- Future.sequence(whatsRunningWhere.map(repository.update))
    } yield success


  def updateModel(whatsRunningWhere: List[ServiceDeploymentInformation]): Future[List[Boolean]] =
    for {
      success           <- Future.sequence(whatsRunningWhere.map(repository.update))
    } yield success

}
