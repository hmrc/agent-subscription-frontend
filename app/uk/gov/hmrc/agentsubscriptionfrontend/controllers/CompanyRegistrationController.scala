/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.{Configuration, Environment}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.CompanyRegistrationForms._
import uk.gov.hmrc.agentsubscriptionfrontend.models.AgentSession
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.Llp
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.{company_registration, llp_interrupt}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext

@Singleton
class CompanyRegistrationController @Inject()(
  val redirectUrlActions: RedirectUrlActions,
  val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService,
  val subscriptionService: SubscriptionService,
  val config: Configuration,
  val env: Environment,
  val metrics: Metrics,
  val subscriptionJourneyService: SubscriptionJourneyService,
  mcc: MessagesControllerComponents,
  companyRegistrationTemplate: company_registration,
  llpInterruptTemplate: llp_interrupt
)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  def showCompanyRegNumberForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        existingSession.companyRegistrationNumber match {
          case Some(crn) =>
            Ok(companyRegistrationTemplate(crnForm.fill(crn)))
          case None => Ok(companyRegistrationTemplate(crnForm))
        }
      }
    }
  }

  def submitCompanyRegNumberForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession: AgentSession) =>
        crnForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(companyRegistrationTemplate(formWithErrors)),
            validCrn =>
              existingSession.utr match {
                case Some(utr) =>
                  subscriptionService.matchCorporationTaxUtrWithCrn(utr, validCrn).flatMap {
                    foundMatch =>
                      if (foundMatch)
                        updateSessionAndRedirect(existingSession.copy(
                          companyRegistrationNumber = Some(validCrn),
                          ctUtrCheckResult = Some(foundMatch),
                          nino = None, // in case they are LLP and re-entered a new CRN that does match
                          dateOfBirth = None,
                          lastNameFromCid = None,
                          dateOfBirthFromCid = None
                        ))(routes.VatDetailsController.showRegisteredForVatForm())
                      else {
                        existingSession.businessType match {
                          case Some(bt) =>
                            if (bt == Llp)
                              updateSessionAndRedirect(existingSession
                                .copy(companyRegistrationNumber = Some(validCrn), ctUtrCheckResult = Some(foundMatch)))(
                                routes.CompanyRegistrationController.showLlpInterrupt())
                            else
                              Redirect(routes.BusinessIdentificationController.showNoMatchFound())
                          case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
                        }
                      }
                  }
                case _ => Redirect(routes.UtrController.showUtrForm())
            }
          )
      }
    }
  }

  def showLlpInterrupt(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (businessType, agentSession) =>
        if (businessType == Llp) Ok(llpInterruptTemplate())
        else {
          updateSessionAndRedirect(AgentSession())(routes.BusinessTypeController.showBusinessTypeForm())
        }
      }
    }
  }

}
