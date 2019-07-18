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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentSubscriptionConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, AuthProviderId}
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionJourneyService @Inject()(agentSubscriptionConnector: AgentSubscriptionConnector)(
  implicit ec: ExecutionContext) {

  def getJourneyRecord(internalId: AuthProviderId)(
    implicit hc: HeaderCarrier): Future[Option[SubscriptionJourneyRecord]] =
    // TODO move logic to backend
    for {
      primaryRecord <- agentSubscriptionConnector.getJourneyByPrimaryId(internalId)
      mappedRecord  <- agentSubscriptionConnector.getJourneyByMappedId(internalId)
    } yield primaryRecord.orElse(mappedRecord)

  def saveJourneyRecord(subscriptionJourneyRecord: SubscriptionJourneyRecord)(
    implicit hc: HeaderCarrier): Future[Unit] =
    agentSubscriptionConnector.createOrUpdate(subscriptionJourneyRecord)

  def saveJourneyRecord(agentSession: AgentSession, authProviderId: AuthProviderId)(
    implicit hc: HeaderCarrier): Future[Unit] = {
    val sjr = SubscriptionJourneyRecord.fromAgentSession(agentSession, authProviderId)
    saveJourneyRecord(sjr)
  }
}
