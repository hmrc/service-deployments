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

import java.util.concurrent.TimeUnit

import com.kenshoo.play.metrics.MetricsFilter
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.mvc.{EssentialAction, EssentialFilter, Filters}
import play.api.{Application, Configuration, GlobalSettings, Play}
import uk.gov.hmrc.play.config.{ControllerConfig, RunMode}
import uk.gov.hmrc.play.filters.{NoCacheFilter, RecoveryFilter}
import uk.gov.hmrc.play.graphite.GraphiteConfig
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter
import uk.gov.hmrc.play.microservice.bootstrap.JsonErrorHandling
import uk.gov.hmrc.play.microservice.bootstrap.Routing.RemovingOfTrailingSlashes

import scala.concurrent.duration.FiniteDuration


object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object MicroserviceLoggingFilter extends LoggingFilter {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

trait MicroserviceFilters {
  def loggingFilter: LoggingFilter
  def metricsFilter: MetricsFilter = MetricsFilter

  protected lazy val defaultMicroserviceFilters: Seq[EssentialFilter] = Seq(
    Some(metricsFilter),
    Some(loggingFilter),
    Some(NoCacheFilter),
    Some(RecoveryFilter)).flatten

  def microserviceFilters: Seq[EssentialFilter] = defaultMicroserviceFilters
}

object MicroserviceGlobal extends GlobalSettings
  with MicroserviceFilters with GraphiteConfig with RemovingOfTrailingSlashes with JsonErrorHandling  with RunMode {

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")
  override val loggingFilter = MicroserviceLoggingFilter

  override def onStart(app: Application): Unit = {
    if (ServiceReleasesConfig.schedulerEnabled)
      Scheduler.start(FiniteDuration(1, TimeUnit.HOURS))

    super.onStart(app)
  }

  override def doFilter(a: EssentialAction): EssentialAction = {
    Filters(super.doFilter(a), microserviceFilters: _*)
  }
}
