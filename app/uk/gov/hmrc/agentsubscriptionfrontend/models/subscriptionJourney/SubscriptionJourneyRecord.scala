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

package uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.LocalDateTime
import java.util.UUID

/** A Mongo record which represents the user's current journey in setting up a new MTD Agent Services account, with their existing relationships.
  */
final case class SubscriptionJourneyRecord(
  authProviderId: AuthProviderId,
  continueId: Option[String] = None, // once allocated, should not be changed?
  businessDetails: BusinessDetails,
  amlsData: Option[AmlsData] = None,
  userMappings: List[UserMapping] = List.empty,
  mappingComplete: Boolean = false,
  cleanCredsAuthProviderId: Option[AuthProviderId] = None,
  lastModifiedDate: Option[LocalDateTime] = None,
  contactEmailData: Option[ContactEmailData] = None,
  contactTradingNameData: Option[ContactTradingNameData] = None,
  contactTradingAddressData: Option[ContactTradingAddressData] = None,
  contactTelephoneData: Option[ContactTelephoneData] = None,
  verifiedEmails: VerifiedEmails = VerifiedEmails(emails = Set.empty)
) {
  def effectiveEmail: Option[String] = contactEmailData match {
    case None                              => None
    case Some(ced) if ced.useBusinessEmail => businessDetails.registration.flatMap(_.emailAddress)
    case Some(ced)                         => ced.contactEmail
  }
  def emailNeedsVerifying(authEmail: Option[String]): Boolean =
    if (effectiveEmail.map(_.toLowerCase) == authEmail.map(_.toLowerCase)) false
    else effectiveEmail.exists(email => !verifiedEmails.emails.contains(email))
}

object SubscriptionJourneyRecord {

  import MongoLocalDateTimeFormat._

  def databaseWrites(crypto: Encrypter with Decrypter): Writes[SubscriptionJourneyRecord] =
    ((JsPath \ "authProviderId").write[AuthProviderId] and
      (JsPath \ "continueId").writeNullable[String] and
      (JsPath \ "businessDetails").write[BusinessDetails](BusinessDetails.databaseFormat(crypto)) and
      (JsPath \ "amlsData").writeNullable[AmlsData] and
      (JsPath \ "userMappings").write[List[UserMapping]] and
      (JsPath \ "mappingComplete").write[Boolean] and
      (JsPath \ "cleanCredsAuthProviderId").writeNullable[AuthProviderId] and
      (JsPath \ "lastModifiedDate").writeNullable[LocalDateTime] and
      (JsPath \ "contactEmailData").writeNullable[ContactEmailData](ContactEmailData.databaseFormat(crypto)) and
      (JsPath \ "contactTradingNameData").writeNullable[ContactTradingNameData](
        ContactTradingNameData.databaseFormat(crypto)
      ) and
      (JsPath \ "contactTradingAddressData").writeNullable[ContactTradingAddressData](
        ContactTradingAddressData.databaseFormat(crypto)
      ) and
      (JsPath \ "contactTelephoneData").writeNullable[ContactTelephoneData](
        ContactTelephoneData.databaseFormat(crypto)
      ) and
      (JsPath \ "verifiedEmails").write[VerifiedEmails](
        VerifiedEmails.databaseFormat(crypto)
      ))(
      unlift(SubscriptionJourneyRecord.unapply)
    )

  def databaseReads(crypto: Encrypter with Decrypter): Reads[SubscriptionJourneyRecord] =
    ((JsPath \ "authProviderId").read[AuthProviderId] and
      (JsPath \ "continueId").readNullable[String] and
      (JsPath \ "businessDetails").read[BusinessDetails](BusinessDetails.databaseFormat(crypto)) and
      (JsPath \ "amlsData").readNullable[AmlsData] and
      (JsPath \ "userMappings").read[List[UserMapping]] and
      (JsPath \ "mappingComplete").read[Boolean] and
      (JsPath \ "cleanCredsAuthProviderId").readNullable[AuthProviderId] and
      (JsPath \ "lastModifiedDate").readNullable[LocalDateTime] and
      (JsPath \ "contactEmailData").readNullable[ContactEmailData](ContactEmailData.databaseFormat(crypto)) and
      (JsPath \ "contactTradingNameData").readNullable[ContactTradingNameData](
        ContactTradingNameData.databaseFormat(crypto)
      ) and
      (JsPath \ "contactTradingAddressData").readNullable[ContactTradingAddressData](
        ContactTradingAddressData.databaseFormat(crypto)
      ) and
      (JsPath \ "contactTelephoneData").readNullable[ContactTelephoneData](
        ContactTelephoneData.databaseFormat(crypto)
      ) and
      (JsPath \ "verifiedEmails")
        .read[VerifiedEmails](VerifiedEmails.databaseFormat(crypto)))(SubscriptionJourneyRecord.apply _)

  def databaseFormat(crypto: Encrypter with Decrypter): Format[SubscriptionJourneyRecord] =
    Format(databaseReads(crypto), sjr => databaseWrites(crypto).writes(sjr))

  implicit val writes: Writes[SubscriptionJourneyRecord] = Json.writes[SubscriptionJourneyRecord]
  implicit val reads: Reads[SubscriptionJourneyRecord] = Json.reads[SubscriptionJourneyRecord]

  def fromAgentSession(
    agentSession: AgentSession,
    authProviderId: AuthProviderId,
    cleanCredsAuthProviderId: Option[AuthProviderId] = None
  ): SubscriptionJourneyRecord =
    SubscriptionJourneyRecord(
      authProviderId = authProviderId,
      continueId = Some(UUID.randomUUID().toString.replace("-", "")),
      businessDetails = BusinessDetails(
        businessType = agentSession.businessType.getOrElse(throw new RuntimeException("no business type found in agent session")),
        utr = agentSession.utr.map(_.value).getOrElse(throw new RuntimeException("no utr found in agent session")),
        postcode = agentSession.postcode.map(_.value).getOrElse(throw new RuntimeException("no postcode found in agent session")),
        registration = agentSession.registration,
        nino = agentSession.nino.map(_.value),
        companyRegistrationNumber = agentSession.companyRegistrationNumber,
        dateOfBirth = agentSession.dateOfBirth
      ),
      cleanCredsAuthProviderId = cleanCredsAuthProviderId
    )
}
