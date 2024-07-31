/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.data.Form
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.ninoForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{Llp, Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessType}
import uk.gov.hmrc.agentsubscriptionfrontend.service.{MongoDBSessionStoreService, SubscriptionJourneyService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TaxIdentifierFormatters.normalizeNino
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.national_insurance_number
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NationalInsuranceController @Inject() (
  val redirectUrlActions: RedirectUrlActions,
  val authConnector: AuthConnector,
  val sessionStoreService: MongoDBSessionStoreService,
  val env: Environment,
  val config: Configuration,
  val metrics: Metrics,
  val subscriptionJourneyService: SubscriptionJourneyService,
  val subscriptionService: SubscriptionService,
  mcc: MessagesControllerComponents,
  nationalInsuranceNumberTemplate: national_insurance_number
)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  /** In-case of SoleTrader or Partnerships, we should display NI and DOB pages based on if nino and dob exist or not. We need to force users to go
    * through these pages, hence the below checks
    */
  def showNationalInsuranceNumberForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withValidSession { (_, existingSession) =>
        withValidBusinessType(existingSession) { businessType =>
          val backUrl = Some(backUrlForBusinessType(businessType))
          if (businessType == Llp)
            Ok(nationalInsuranceNumberTemplate(getForm(existingSession.nino), businessType, backUrl))
          else
            agent.authNino match {
              case Some(_) =>
                Ok(nationalInsuranceNumberTemplate(getForm(existingSession.nino), businessType, backUrl))
              case None => Redirect(routes.VatDetailsController.showRegisteredForVatForm())
            }
        }
      }
    }
  }

  // noinspection ScalaStyle
  def submitNationalInsuranceNumberForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withValidSession { (_, existingSession) =>
        withValidBusinessType(existingSession) { businessType =>
          ninoForm
            .bindFromRequest()
            .fold(
              formWithErrors => {
                val backUrl = Some(backUrlForBusinessType(businessType))
                Ok(nationalInsuranceNumberTemplate(formWithErrors, businessType, backUrl))
              },
              validNino => {
                def ninosMatched: Boolean = agent.authNino.flatMap(normalizeNino) == normalizeNino(validNino.value)
                if (ninosMatched || businessType == Llp) {
                  subscriptionService
                    .getDesignatoryDetails(validNino)
                    .map(_.person)
                    .flatMap {
                      case Some(person) if person.deceased.contains(true) =>
                        logger.warn(s"Could not register agent as the NINO is marked as deceased.")
                        Redirect(routes.BusinessIdentificationController.showCannotConfirmIdentity())
                      case Some(person) if person.dateOfBirth.isEmpty && businessType != Llp =>
                        logger.warn("no DateOfBirth in the /citizen-details response for logged in agent")
                        updateSessionAndRedirect(existingSession.copy(nino = Some(validNino)))(routes.VatDetailsController.showRegisteredForVatForm())
                      case Some(person) if person.dateOfBirth.nonEmpty && businessType != Llp =>
                        updateSessionAndRedirect(
                          existingSession
                            .copy(nino = Some(validNino), dateOfBirthFromCid = person.dateOfBirth)
                        )(routes.DateOfBirthController.showDateOfBirthForm())
                      case Some(person) if person.lastName.nonEmpty && person.dateOfBirth.nonEmpty =>
                        updateSessionAndRedirect(
                          existingSession
                            .copy(nino = Some(validNino), dateOfBirthFromCid = person.dateOfBirth, lastNameFromCid = person.lastName)
                        )(routes.DateOfBirthController.showDateOfBirthForm())
                      case _ =>
                        logger.warn(s"business type $businessType no lastName and or no dob from CiD")
                        if (businessType != Llp)
                          Redirect(routes.VatDetailsController.showRegisteredForVatForm())
                        else
                          Redirect(routes.BusinessIdentificationController.showNoMatchFound())
                    }
                } else {
                  logger.warn(s"Auth Nino did not match ValidNino for businessType $businessType")
                  Future.successful(Redirect(routes.BusinessIdentificationController.showNoMatchFound()))
                }
              }
            )
        }
      }
    }
  }

  private def getForm(sessionNino: Option[Nino]): Form[Nino] = sessionNino match {
    case Some(nino) => ninoForm.fill(nino)
    case None       => ninoForm
  }

  private def backUrlForBusinessType(businessType: BusinessType): String =
    businessType match {
      case Llp => routes.CompanyRegistrationController.showLlpInterrupt().url
      case _   => routes.PostcodeController.showPostcodeForm().url
    }

  private def withValidBusinessType(
    agentSession: AgentSession
  )(result: (BusinessType => Future[Result]))(implicit request: Request[_]): Future[Result] =
    agentSession.businessType match {
      case b @ Some(SoleTrader | Partnership | Llp) => result(b.get)
      case _ =>
        updateSessionAndRedirect(AgentSession())(routes.BusinessTypeController.showBusinessTypeForm())
    }
}
