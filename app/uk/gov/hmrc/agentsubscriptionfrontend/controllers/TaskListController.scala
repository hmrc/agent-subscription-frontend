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
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.TaskListFlags
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService, TaskListService}
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.{ExecutionContext, Future}

class TaskListController @Inject()(
  override val authConnector: AuthConnector,
  agentAssuranceConnector: AgentAssuranceConnector,
  continueUrlActions: ContinueUrlActions,
  val sessionStoreService: SessionStoreService,
  override val subscriptionJourneyService: SubscriptionJourneyService,
  taskListService: TaskListService)(
  implicit override implicit val appConfig: AppConfig,
  metrics: Metrics,
  override val messagesApi: MessagesApi,
  val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig, subscriptionJourneyService)
    with SessionBehaviour {

  def showTaskList: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingOrSubscribedAgent { agent =>
      agent.subscriptionJourneyRecord match {
        case Some(record)  => taskListService.getTaskListFlags(record).map(flags => Ok(html.task_list(flags)))
        case None          => Future successful InternalServerError("No journey record found for task list")
      }
    }
    {
      // TODO - do we will need to handle this case?  If so, we'll need access to the record...
      sessionStoreService.fetchAgentSession.flatMap {
        case Some(session) if session.taskListFlags.businessTaskComplete => Future successful Ok(html.task_list(session.taskListFlags))
        case _ => Future successful Redirect(appConfig.agentServicesAccountUrl)
      }
    }

  }
}
