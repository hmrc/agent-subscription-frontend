/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.models

import play.api.i18n.Lang
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import play.api.libs.json._

case class Agency(name: String, address: DesAddress, email: String)

object Agency {
  implicit val formatDesAddress: Format[DesAddress] = Json.format[DesAddress]
  implicit val formatAgency: Format[Agency] = Json.format[Agency]
}

case class SubscriptionRequestKnownFacts(postcode: String)

object SubscriptionRequestKnownFacts {
  implicit val format: Format[SubscriptionRequestKnownFacts] = Json.format[SubscriptionRequestKnownFacts]
}

case class SubscriptionRequest(
  utr: Utr,
  knownFacts: SubscriptionRequestKnownFacts,
  agency: Agency,
  langForEmail: Option[Lang],
  amlsDetails: Option[AmlsDetails]
)

object SubscriptionRequest {
  implicit val format: Format[SubscriptionRequest] = Json.format[SubscriptionRequest]
}

case class CompletePartialSubscriptionBody(utr: Utr, knownFacts: SubscriptionRequestKnownFacts, langForEmail: Option[Lang])

object CompletePartialSubscriptionBody {
  implicit val format: Format[CompletePartialSubscriptionBody] = Json.format[CompletePartialSubscriptionBody]
}
