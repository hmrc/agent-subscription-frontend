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

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AnyContent, Request, _}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{AgentRequest, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentSubscriptionConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.{KnownFactsResult, Registration}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.passcode.authentication.{PasscodeAuthenticationProvider, PasscodeVerificationConfig}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

@Singleton
class CheckAgencyController @Inject()
(override val messagesApi: MessagesApi,
 override val authConnector: AuthConnector,
 override val config: PasscodeVerificationConfig,
 override val passcodeAuthenticationProvider: PasscodeAuthenticationProvider,
 val agentSubscriptionConnector: AgentSubscriptionConnector,
 val sessionStoreService: SessionStoreService)
(implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport with AuthActions with SessionDataMissing {

  private val knownFactsForm = Form[KnownFacts](
    mapping(
      "utr" -> FieldMappings.utr,
      "postcode" -> FieldMappings.postcode
    )(KnownFacts.apply)(KnownFacts.unapply)
  )

  val showHasOtherEnrolments: Action[AnyContent] = AuthorisedWithSubscribingAgent { implicit authContext =>
    implicit request =>
      Ok(html.has_other_enrolments())
  }

  val showCheckAgencyStatus: Action[AnyContent] = AuthorisedWithSubscribingAgent {
    implicit authContext =>
      implicit request =>
        hasMtdEnrolment match {
          case true => Redirect(routes.CheckAgencyController.showAlreadySubscribed())
          case false => Ok(html.check_agency_status(knownFactsForm))
        }
  }

  private def hasMtdEnrolment(implicit request: AgentRequest[_]): Boolean = request.enrolments.exists(_.key == "HMRC-AS-AGENT")

  val checkAgencyStatus: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync { implicit authContext: AuthContext =>
    implicit request =>
      knownFactsForm.bindFromRequest().fold(
        formWithErrors => {
          Future successful Ok(html.check_agency_status(formWithErrors))
        },
        knownFacts => checkAgencyStatusGivenValidForm(knownFacts)
      )
  }

  private def checkAgencyStatusGivenValidForm(knownFacts: KnownFacts)
                                             (implicit authContext: AuthContext, request: Request[AnyContent]): Future[Result] = {
    agentSubscriptionConnector.getRegistration(knownFacts.utr, knownFacts.postcode) flatMap { maybeRegistration: Option[Registration] =>
      maybeRegistration match {
        case Some(Registration(Some(name), isSubscribedToAgentServices)) =>
          sessionStoreService.cacheKnownFactsResult(KnownFactsResult(
            utr = knownFacts.utr,
            postcode = knownFacts.postcode,
            taxpayerName = name,
            isSubscribedToAgentServices = isSubscribedToAgentServices)).map { _ =>
            Redirect(routes.CheckAgencyController.showConfirmYourAgency())
          }
        case Some(_) => throw new IllegalStateException(s"The agency with UTR ${knownFacts.utr} has no organisation name.")
        case None => Future successful Redirect(routes.CheckAgencyController.showNoAgencyFound())
      }
    }
  }

  val showNoAgencyFound: Action[AnyContent] = AuthorisedWithSubscribingAgent {
    implicit authContext =>
      implicit request =>
        Ok(html.no_agency_found())
  }

  private def lookupNextPageUrl(isSubscribedToAgentServices: Boolean): String =
    if (isSubscribedToAgentServices)
      routes.CheckAgencyController.showAlreadySubscribed().url
    else
      routes.CheckAgencyController.showNotSubscribed().url

  val showConfirmYourAgency: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync {
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

  val showAlreadySubscribed: Action[AnyContent] = AuthorisedWithSubscribingAgent { implicit authContext =>
    implicit request =>
      Ok(html.already_subscribed())
  }

  val showNotSubscribed: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync { implicit authContext =>
    implicit request =>
      sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
        Ok(html.not_subscribed(registrationName = knownFactsResult.taxpayerName))
      }.getOrElse {
        sessionMissingRedirect()
      })
  }
}
