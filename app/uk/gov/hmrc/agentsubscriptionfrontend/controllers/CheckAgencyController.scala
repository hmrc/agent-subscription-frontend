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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import javax.inject.{Inject, Named, Singleton}

import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AnyContent, Request, _}
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AuditService
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{AgentRequest, AuthActions, NoOpRegimeWithContinueUrl}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.{AgentAssuranceConnector, AgentSubscriptionConnector}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AssuranceResults, KnownFactsResult, Registration}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.passcode.authentication.{PasscodeAuthenticationProvider, PasscodeVerificationConfig}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

object CheckAgencyController {
  val knownFactsForm: Form[KnownFacts] = Form[KnownFacts](
    mapping(
      "utr" -> FieldMappings.utr,
      "postcode" -> FieldMappings.postcode
    )(KnownFacts.apply)(KnownFacts.unapply)
  )
}

@Singleton
class CheckAgencyController @Inject()
(@Named("agentAssuranceFlag") agentAssuranceFlag: Boolean,
 val agentAssuranceConnector: AgentAssuranceConnector,
 override val messagesApi: MessagesApi,
 override val authConnector: AuthConnector,
 override val config: PasscodeVerificationConfig,
 override val passcodeAuthenticationProvider: PasscodeAuthenticationProvider,
 val agentSubscriptionConnector: AgentSubscriptionConnector,
 val sessionStoreService: SessionStoreService,
 continueUrlActions: ContinueUrlActions,
 auditService: AuditService)
(implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport with AuthActions with SessionDataMissing {

  import continueUrlActions._

  val showHasOtherEnrolments: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() { implicit authContext =>
    implicit request =>
      Future successful Ok(html.has_other_enrolments())
  }

  private def hasMtdEnrolment(implicit request: AgentRequest[_]): Boolean = request.enrolments.exists(_.key == "HMRC-AS-AGENT")

  def showCheckAgencyStatus: Action[AnyContent] = {
    AuthorisedWithSubscribingAgentAsync(NoOpRegimeWithContinueUrl) {
      implicit authContext =>
        implicit request =>
          withMaybeContinueUrlCached {
            hasMtdEnrolment match {
              case true => Future successful Redirect(routes.CheckAgencyController.showAlreadySubscribed())
              case false => Future successful Ok(html.check_agency_status(CheckAgencyController.knownFactsForm))
            }
          }
    }
  }

  private def lookupNextPageUrl(isSubscribedToAgentServices: Boolean): String =
    if (isSubscribedToAgentServices)
      routes.CheckAgencyController.showAlreadySubscribed().url
    else routes.SubscriptionController.showInitialDetails().url


  val checkAgencyStatus: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() {
    implicit authContext: AuthContext =>
      implicit request =>
        CheckAgencyController.knownFactsForm.bindFromRequest().fold(
          formWithErrors => {
            Future successful Ok(html.check_agency_status(formWithErrors))
          },
          knownFacts => checkAgencyStatusGivenValidForm(knownFacts)
        )
  }

  private def checkAgencyStatusGivenValidForm(knownFacts: KnownFacts)
                                             (implicit authContext: AuthContext, request: AgentRequest[AnyContent]): Future[Result] = {
    def assureIsAgent(): Future[Option[AssuranceResults]] = {
      if (agentAssuranceFlag) {
        val futurePaye = agentAssuranceConnector.hasAcceptableNumberOfPayeClients
        val futureSA = agentAssuranceConnector.hasAcceptableNumberOfSAClients

        for {
          hasAcceptableNumberOfPayeClients <- futurePaye
          hasAcceptableNumberOfSAClients <- futureSA
        } yield Some(AssuranceResults(hasAcceptableNumberOfPayeClients, hasAcceptableNumberOfSAClients))
      }
      else Future.successful(None)
    }

    def decideBasedOn: Option[AssuranceResults] => Result = {
      case Some(AssuranceResults(false,false)) => Redirect(routes.StartController.setupIncomplete())
      case _  => Redirect(routes.CheckAgencyController.showConfirmYourAgency())
    }

    agentSubscriptionConnector.getRegistration(knownFacts.utr, knownFacts.postcode) flatMap { maybeRegistration: Option[Registration] =>
      maybeRegistration match {
        case Some(Registration(Some(taxpayerName), isSubscribedToAgentServices)) =>

          for {
            assuranceResults <- assureIsAgent()
            knownFactsResult = KnownFactsResult(knownFacts.utr, knownFacts.postcode, taxpayerName, isSubscribedToAgentServices)
            _ <- sessionStoreService.cacheKnownFactsResult(knownFactsResult)
            _ <- assuranceResults.map(auditService.sendAgentAssuranceAuditEvent(knownFactsResult, _)).getOrElse(Future.successful(()))
          } yield decideBasedOn(assuranceResults)

        case Some(_) => throw new IllegalStateException(s"The agency with UTR ${knownFacts.utr} has no organisation name.")
        case None => Future successful Redirect(routes.CheckAgencyController.showNoAgencyFound())
      }
    }
  }

  val showNoAgencyFound: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() {
    implicit authContext =>
      implicit request =>
        Future successful Ok(html.no_agency_found())
  }

  val showConfirmYourAgency: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() {
    implicit authContext =>
      implicit request =>
        sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
          Ok(html.confirm_your_agency(
            registrationName = knownFactsResult.taxpayerName,
            postcode = knownFactsResult.postcode,
            utr = knownFactsResult.utr,
            nextPageUrl = lookupNextPageUrl(knownFactsResult.isSubscribedToAgentServices)))
        }.getOrElse {
          sessionMissingRedirect()
        })
  }

  val showAlreadySubscribed: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() { implicit authContext =>
    implicit request =>
      Future successful Ok(html.already_subscribed())
  }
}