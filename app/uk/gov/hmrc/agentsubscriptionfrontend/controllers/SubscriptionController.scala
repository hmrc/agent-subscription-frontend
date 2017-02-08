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

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.agentsubscriptionfrontend.auth.NoOpRegime
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.{Actions, AuthContext}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

@Singleton
class SubscriptionController @Inject() (override val messagesApi: MessagesApi, override val authConnector: AuthConnector) (implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport with Actions {

  private[controllers] val showCheckAgencyStatusBody: (AuthContext) => (Request[AnyContent]) => Future[Result] = {
    implicit authContext => implicit request =>
      ensureAffinityGroupIsAgent {
        Ok(html.subscribe())
      }
  }

  val showCheckAgencyStatus: Action[AnyContent] = AuthorisedFor(NoOpRegime, GGConfidence).async(showCheckAgencyStatusBody)

  val showNonAgentNextSteps: Action[AnyContent] = AuthorisedFor(NoOpRegime, GGConfidence).async { implicit authContext: AuthContext => implicit request =>
    Future successful Ok(html.non_agent_next_steps())
  }

  val showSubscriptionDetails: Action[AnyContent] = Action.async { implicit request =>
    Future successful Ok(html.subscription_details())
  }


  private def ensureAffinityGroupIsAgent(action: => Result)(implicit authContext: AuthContext, hc: HeaderCarrier) =
    authConnector.getUserDetails(authContext).map { userDetailsResponse =>
      val affinityGroup = (userDetailsResponse.json \ "affinityGroup").as[String]
      if (affinityGroup == "Agent") {
        action
      } else {
        redirectToNonAgentNextSteps
      }
    }

  private def redirectToNonAgentNextSteps =
    SeeOther(routes.SubscriptionController.showNonAgentNextSteps().url)
}
