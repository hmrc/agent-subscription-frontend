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

package uk.gov.hmrc.agentsubscriptionfrontend.config

import javax.inject.Inject

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Request
import play.api.{Application, Configuration, Play}
import play.twirl.api.Html
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.crypto.ApplicationCrypto.verifyConfiguration
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.frontend.bootstrap.DefaultFrontendGlobal
import uk.gov.hmrc.play.frontend.filters.{FrontendAuditFilter, FrontendLoggingFilter, MicroserviceFilterSupport}


object FrontendGlobal extends DefaultFrontendGlobal {

  implicit lazy val appConfig: AppConfig = current.injector.instanceOf[AppConfig]
  override lazy val auditConnector = current.injector.instanceOf[AuditConnector]
  override lazy val loggingFilter = current.injector.instanceOf[LoggingFilter]
  override lazy val frontendAuditFilter = current.injector.instanceOf[AuditFilter]

  override def onStart(app: Application) {
    super.onStart(app)
    verifyConfiguration()
  }

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit rh: Request[_]): Html =
    html.error_template(pageTitle, heading, message)

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")
}

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

class LoggingFilter extends FrontendLoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

class AuditFilter @Inject()(val auditConnector: AuditConnector) extends FrontendAuditFilter with RunMode with AppName with MicroserviceFilterSupport {

  override lazy val maskedFormFields = Seq("password")

  override lazy val applicationPort = None

  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}
