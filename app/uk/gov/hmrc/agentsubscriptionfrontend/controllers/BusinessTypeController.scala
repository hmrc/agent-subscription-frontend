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
import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.businessTypeForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.AgentSession
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.{ExecutionContext, Future}

class BusinessTypeController @Inject()(
  override val redirectUrlActions: RedirectUrlActions,
  override val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService,
  subscriptionService: SubscriptionService,
  override val subscriptionJourneyService: SubscriptionJourneyService)(
  implicit override val metrics: Metrics,
  override val appConfig: AppConfig,
  val ec: ExecutionContext,
  override val messagesApi: MessagesApi)
    extends AgentSubscriptionBaseController(authConnector, redirectUrlActions, appConfig, subscriptionJourneyService)
    with SessionBehaviour {

  def redirectToBusinessTypeForm: Action[AnyContent] = Action.async { implicit request =>
    Redirect(routes.BusinessTypeController.showBusinessTypeForm())
  }

  def showBusinessTypeForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      redirectUrlActions.withMaybeRedirectUrlCached {
        agent.subscriptionJourneyRecord match {
          case Some(_) => Future.successful(Redirect(routes.TaskListController.showTaskList()))
          case None =>
            sessionStoreService.fetchAgentSession.flatMap {
              case Some(agentSession) =>
                agentSession.businessType match {
                  case Some(businessType) =>
                    Ok(html.business_type(businessTypeForm.fill(businessType)))
                  case _ => Ok(html.business_type(businessTypeForm))
                }
              case None => Ok(html.business_type(businessTypeForm))
            }
        }
      }
    }
  }

  def submitBusinessTypeForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      businessTypeForm
        .bindFromRequest()
        .fold(
          formWithErrors => Ok(html.business_type(formWithErrors)),
          validatedBusinessType => {
            sessionStoreService.fetchAgentSession
              .flatMap(_.getOrElse(AgentSession()))
              .flatMap { agentSession =>
                updateSessionAndRedirect(agentSession.copy(businessType = Some(validatedBusinessType)))(
                  routes.UtrController.showUtrForm())
              }
          }
        )
    }
  }

}
