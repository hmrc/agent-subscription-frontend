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

import play.api.mvc._
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.ContinueUrlActions
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.Retrievals.authorisedEnrolments
import uk.gov.hmrc.auth.core.{ AuthProviders, AuthorisedFunctions, Enrolment }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ ExecutionContext, Future }

case class Agent(private val enrolments: Set[Enrolment])

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

trait AuthActions extends AuthorisedFunctions with Monitoring {

  protected type AsyncPlayUserRequest = Agent => Future[Result]

  val continueUrlActions: ContinueUrlActions

  private implicit def hc(implicit request: Request[_]): HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

  def withSubscribingAgent[A](body: AsyncPlayUserRequest)(implicit request: Request[A], hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    authorised(AuthProviders(GovernmentGateway))
      .retrieve(authorisedEnrolments) {
        enrolments => body(Agent(enrolments.enrolments))
      }
}
