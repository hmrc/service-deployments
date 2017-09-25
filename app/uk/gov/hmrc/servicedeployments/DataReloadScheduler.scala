/*
 * Copyright 2017 HM Revenue & Customs
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

import akka.actor.ActorSystem

import scala.concurrent.duration._


//object ControllerConfiguration extends ControllerConfig {
//  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
//}

//object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
//  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
//}

//trait MicroserviceFilters {
//
//  def loggingFilter: LoggingFilter
//
//  lazy val appName = Play.current.configuration.getString("appName").getOrElse("APP NAME NOT SET")
//  def metricsFilter: MetricsFilter = Play.current.injector.instanceOf[MetricsFilter]
//
////  protected lazy val defaultMicroserviceFilters: Seq[EssentialFilter] = Seq(
////    Some(metricsFilter),
////    Some(loggingFilter),
////    Some(NoCacheFilter),
////    Some(RecoveryFilter)).flatten
//
//  def microserviceFilters: Seq[EssentialFilter] = defaultMicroserviceFilters
//}

@Singleton
class DataReloadScheduler @Inject()(akkaSystem: ActorSystem,
                                    serviceDeploymentsConfig: ServiceDeploymentsConfig,
                                    updateScheduler: UpdateScheduler) {
//GlobalSettings
//  with MicroserviceFilters
//  with GraphiteConfig
//  with RemovingOfTrailingSlashes
//  with JsonErrorHandling
//  with RunMode {

  if (serviceDeploymentsConfig.schedulerEnabled) {
    updateScheduler.startUpdatingWhatIsRunningWhereModel(10 minutes)
    updateScheduler.startUpdatingDeploymentServiceModel(20 minutes)
  }

//  override val loggingFilter = MicroserviceLoggingFilter
//
//  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")
//
//  override def onStart(app: Application): Unit = {
//
//
//
//    super.onStart(app)
//  }

//  override def doFilter(a: EssentialAction): EssentialAction = {
//    Filters(super.doFilter(a), microserviceFilters: _*)
//  }
}
