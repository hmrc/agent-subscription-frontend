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

package uk.gov.hmrc.agentsubscriptionfrontend.auth

import play.api.Mode
import play.api.mvc.{ Request, Result }
import play.api.mvc.Results._
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.{ ContinueUrlActions, routes }
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.Retrievals.{ affinityGroup, allEnrolments, credentials }
import uk.gov.hmrc.auth.core.retrieve.{ Credentials, ~ }
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.config.AuthRedirects

import scala.concurrent.{ ExecutionContext, Future }

class Agent(private val enrolments: Set[Enrolment], private val creds: Credentials) {

  def hasIRPAYEAGENT: Option[Enrolment] = enrolments.find(e => e.key == "IR-PAYE-AGENT" && e.isActivated)
  def hasIRSAAGENT: Option[Enrolment] = enrolments.find(e => e.key == "IR-SA-AGENT" && e.isActivated)

  def authProviderId = creds.providerId
  def authProviderType = creds.providerType
}

object Agent {

  object hasHmrcAsAgentEnrolment {
    def unapply(agent: Agent): Option[Unit] =
      if (agent.enrolments.exists(_.key == "HMRC-AS-AGENT")) Some(()) else None
  }

  object hasNonEmptyEnrolments {
    def unapply(agent: Agent): Option[Unit] =
      if (agent.enrolments.nonEmpty) Some(()) else None
  }
}

trait AuthActions extends AuthorisedFunctions with AuthRedirects with Monitoring {

  def continueUrlActions: ContinueUrlActions
  def appConfig: AppConfig

  def env = appConfig.environment
  def config = appConfig.configuration

  private implicit def hc(implicit request: Request[_]): HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

  def withSubscribingAgent[A](body: Agent => Future[Result])(implicit request: Request[A], hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    authorised(AuthProviders(GovernmentGateway))
      .retrieve(allEnrolments and affinityGroup and credentials) {
        case enrolments ~ affinity ~ creds =>
          (isAnAgent(affinity), isEnrolledForHmrcAsAgent(enrolments)) match {
            case (true, true) =>
              continueUrlActions.extractContinueUrl.map {
                case Some(continueUrl) =>
                  mark("Count-Subscription-AlreadySubscribed-HasEnrolment-ContinueUrl")
                  Redirect(continueUrl.url)
                case None =>
                  mark("Count-Subscription-AlreadySubscribed-HasEnrolment-AgentServicesAccount")
                  Redirect(appConfig.agentServicesAccountUrl)
              }
            case (true, false) => body(new Agent(enrolments.enrolments, creds))
            case _ =>
              Future successful {
                mark("Count-Subscription-NonAgent")
                Redirect(routes.StartController.showNonAgentNextSteps())
              }
          }
      }.recover {
        case _: NoActiveSession =>
          toGGLogin(if (appConfig.environment.mode.equals(Mode.Dev)) s"http://${request.host}${request.uri}" else s"${request.uri}")
      }

  def withAuthenticatedUser[A](body: => Future[Result])(implicit request: Request[A], hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    authorised(AuthProviders(GovernmentGateway))(body)
      .recover {
        case _: NoActiveSession =>
          toGGLogin(if (appConfig.environment.mode.equals(Mode.Dev)) s"http://${request.host}${request.uri}" else s"${request.uri}")
      }

  private def isAnAgent(affinity: Option[AffinityGroup]): Boolean =
    affinity.contains(AffinityGroup.Agent)

  private def isEnrolledForHmrcAsAgent(enrolments: Enrolments): Boolean =
    enrolments.enrolments.find(_.key equals "HMRC-AS-AGENT").exists(_.isActivated)
}
