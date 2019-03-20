/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}
@Singleton
class CompanyRegistrationController @Inject()(
  val continueUrlActions: ContinueUrlActions,
  override implicit val appConfig: AppConfig,
  override implicit val ec: ExecutionContext,
  override val messagesApi: MessagesApi,
  override val sessionStoreService: SessionStoreService,
  val metrics: Metrics,
  override val authConnector: AuthConnector,
  commonRouting: CommonRouting)
    extends FrontendController with I18nSupport with AuthActions with SessionDataSupport with Monitoring
    with SessionBehaviour {

  import CompanyRegistrationForms._
  import continueUrlActions._

  def showCompanyRegNumberForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withMaybeContinueUrlCached {

        Future.successful(Ok(html.company_registration(crnForm)))
      }
    }
  }

  def submitCompanyRegNumberForm: Action[AnyContent] = {implicit request =>
    withSubscribedAgent{ implicit agent =>
      withMaybeContinueUrlCached {
        withValidBusinessType { businessType =>
          crnForm
            .bindFromRequest()
            .fold(
              formWithErrors => Ok(html.company_registration(formWithErrors)),
              validCrn => {
                sessionStoreService.fetchAgentSession.flatMap {
                  case Some(existingSession) =>
                    sessionStoreService
                      .cacheAgentSession(existingSession.copy(postcode = Some(validPostcode)))
                      .map { _ =>
                        if (businessType == SoleTrader || businessType == Partnership) {
                          Redirect(routes.BusinessIdentificationController.showNationalInsuranceNumberForm())
                        } else if (businessType == LimitedCompany || businessType == Llp) {
                          Redirect(routes.CompanyRegistrationController.showCompanyRegNumberForm())
                        } else {
                          Redirect(routes.BusinessIdentificationController.showBusinessTypeForm())
                        }
                      }
                  case None =>
    }
  }



}
