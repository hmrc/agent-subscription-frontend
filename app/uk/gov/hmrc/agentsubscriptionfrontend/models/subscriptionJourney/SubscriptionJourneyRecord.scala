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

package uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney

import java.time.LocalDateTime

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, OFormat}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, AuthProviderId}

/**
  * A Mongo record which represents the user's current journey in setting up a new
  * MTD Agent Services account, with their existing relationships.
  *
  */
final case class SubscriptionJourneyRecord(
  authProviderId: AuthProviderId,
  continueId: Option[String] = None, // once allocated, should not be changed?
  businessDetails: BusinessDetails,
  amlsData: Option[AmlsData] = None,
  userMappings: List[UserMapping] = List.empty,
  mappingComplete: Boolean = false,
  cleanCredsInternalId: Option[AuthProviderId] = None,
  subscriptionCreated: Boolean = false,
  lastModifiedDate: Option[LocalDateTime] = None)

object SubscriptionJourneyRecord {

  import MongoLocalDateTimeFormat._

  implicit val subscriptionJourneyFormat: OFormat[SubscriptionJourneyRecord] =
    ((JsPath \ "authProviderId").format[AuthProviderId] and
      (JsPath \ "continueId").formatNullable[String] and
      (JsPath \ "businessDetails").format[BusinessDetails] and
      (JsPath \ "amlsData").formatNullable[AmlsData] and
      (JsPath \ "userMappings").format[List[UserMapping]] and
      (JsPath \ "mappingComplete").format[Boolean] and
      (JsPath \ "cleanCredsInternalId").formatNullable[AuthProviderId] and
      (JsPath \ "subscriptionCreated").format[Boolean] and
      (JsPath \ "lastModifiedDate")
        .formatNullable[LocalDateTime])(SubscriptionJourneyRecord.apply, unlift(SubscriptionJourneyRecord.unapply))

  def fromAgentSession(agentSession: AgentSession, authProviderId: AuthProviderId): SubscriptionJourneyRecord =
    SubscriptionJourneyRecord(
      authProviderId = authProviderId,
      continueId = None,
      businessDetails = BusinessDetails(
        businessType =
          agentSession.businessType.getOrElse(throw new RuntimeException("no business type found in agent session")),
        utr = agentSession.utr.getOrElse(throw new RuntimeException("no utr found in agent session")),
        postcode = agentSession.postcode.getOrElse(throw new RuntimeException("no postcode found in agent session")),
        registration = agentSession.registration,
        nino = agentSession.nino,
        companyRegistrationNumber = agentSession.companyRegistrationNumber,
        dateOfBirth = agentSession.dateOfBirth
      )
    )

}
