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

import play.api.mvc.Results._
import play.api.mvc.{Call, Request, RequestHeader, Result}
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessType}
import uk.gov.hmrc.agentsubscriptionfrontend.service.{MongoDBSessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import scala.concurrent.{ExecutionContext, Future}

trait SessionBehaviour {

  val sessionStoreService: MongoDBSessionStoreService
  implicit val ec: ExecutionContext
  val subscriptionJourneyService: SubscriptionJourneyService
  implicit val crypto: Encrypter with Decrypter
  protected def withValidSession(body: (BusinessType, AgentSession) => Future[Result])(implicit request: Request[_]): Future[Result] =
    sessionStoreService.fetchAgentSession.flatMap {
      case Some(agentSession) =>
        agentSession.businessType match {
          case Some(businessType) =>
            body(businessType, agentSession)
          case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
        }
      case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
    }

  protected def updateSessionAndRedirect(updatedSession: AgentSession)(redirectTo: Call)(implicit request: Request[_]): Future[Result] =
    sessionStoreService
      .cacheAgentSession(updatedSession)
      .map(_ => Redirect(redirectTo))

  protected def updateSubscriptionJourneyRecordAndRedirect(subscriptionJourneyRecord: SubscriptionJourneyRecord)(redirectTo: Call)(implicit
    rh: RequestHeader
  ): Future[Result] =
    subscriptionJourneyService.saveJourneyRecord(subscriptionJourneyRecord).map(_ => Redirect(redirectTo))
}
