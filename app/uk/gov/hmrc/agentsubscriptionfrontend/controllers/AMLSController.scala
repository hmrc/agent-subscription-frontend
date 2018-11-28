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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AnyContent, _}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.config.amls.AMLSLoader
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future

@Singleton
class AMLSController @Inject()(
  override val messagesApi: MessagesApi,
  override val authConnector: AuthConnector,
  implicit override val appConfig: AppConfig,
  override val continueUrlActions: ContinueUrlActions,
  override val metrics: Metrics,
  override val sessionStoreService: SessionStoreService)
    extends FrontendController with I18nSupport with AuthActions with SessionDataMissing with Monitoring {

  import AMLSForms._

  private val amlsBodies: Map[String, String] = AMLSLoader.load("/amls.csv")

  val showMoneyLaunderingComplianceForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future.successful(Ok(html.money_laundering_compliance(amlsForm(amlsBodies.keys.toSet), amlsBodies)))
    }
  }

  def submitMoneyLaunderingComplianceForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      amlsForm(amlsBodies.keys.toSet)
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(Ok(html.money_laundering_compliance(formWithErrors, amlsBodies))),
          validForm => {
            println(validForm)
            Future.successful(Ok)
          }
        )
    }

  }
}
