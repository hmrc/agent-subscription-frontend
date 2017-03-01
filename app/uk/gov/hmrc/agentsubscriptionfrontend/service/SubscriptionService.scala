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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import javax.inject.{Inject, Singleton}

import play.api.http.Status
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentSubscriptionConnector
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.SubscriptionDetails
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.play.http.{HeaderCarrier, Upstream4xxResponse}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionService @Inject() (agentSubscriptionConnector: AgentSubscriptionConnector) {

  def subscribeAgencyToMtd(utr: String, knownFactsPostcode: String, subscriptionDetails: SubscriptionDetails)
                          (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[Int, Arn]] = {
    val request = SubscriptionRequest(utr, KnownFacts(knownFactsPostcode), Agency(
      name = subscriptionDetails.name,
      email = subscriptionDetails.email,
      telephone = subscriptionDetails.telephone,
      address = Address(addressLine1 = subscriptionDetails.addressLine1,
        addressLine2 = subscriptionDetails.addressLine2,
        addressLine3 = subscriptionDetails.addressLine3,
        postcode = subscriptionDetails.postcode,
        countryCode = "GB")
    ))

    agentSubscriptionConnector.subscribeAgencyToMtd(request) map { x =>
      Right(x)
    } recover {
      case e: Upstream4xxResponse if Seq(Status.FORBIDDEN, Status.CONFLICT) contains e.upstreamResponseCode => Left(e.upstreamResponseCode)
      case e => throw e
    }
  }
}
