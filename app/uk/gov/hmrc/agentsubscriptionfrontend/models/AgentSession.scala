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

package uk.gov.hmrc.agentsubscriptionfrontend.models

import play.api.libs.json.{Format, JsResult, JsValue, Json, Reads, Writes}
import uk.gov.hmrc.agentsubscriptionfrontend.util.EncryptionUtils.decryptOptString
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

/** Holds data about a non-MTD agent's initial onboarding session before they have a Journey Subscription Record Just holds data about the business
  * identification step, which occurs before the record is created.
  */
case class AgentSession(
  businessType: Option[BusinessType] = None,
  utr: Option[String] = None,
  postcode: Option[String] = None,
  nino: Option[String] = None,
  companyRegistrationNumber: Option[CompanyRegistrationNumber] = None,
  dateOfBirth: Option[DateOfBirth] = None, // this is user entered DOB
  registeredForVat: Option[String] = None,
  vatDetails: Option[VatDetails] = None,
  registration: Option[Registration] = None,
  dateOfBirthFromCid: Option[DateOfBirth] = None, // just caching this dob from CID so we dont need to make multiple calls to CID
  clientCount: Option[Int] = None,
  lastNameFromCid: Option[String] = None,
  ctUtrCheckResult: Option[Boolean] = None,
  isMAA: Option[Boolean] = None
)

object AgentSession {
  def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[AgentSession] = {

    def reads(json: JsValue): JsResult[AgentSession] =
      for {
        businessType <- (json \ "businessType").validateOpt[BusinessType]
        utr = decryptOptString("utr", json)
        postcode = decryptOptString("postcode", json)
        nino = decryptOptString("nino", json)
        companyRegistrationNumber <- (json \ "companyRegistrationNumber").validateOpt[CompanyRegistrationNumber]
        dateOfBirth               <- (json \ "dateOfBirth").validateOpt[DateOfBirth](DateOfBirth.databaseFormat(crypto))
        registeredForVat          <- (json \ "registeredForVat").validateOpt[String]
        vatDetails                <- (json \ "vatDetails").validateOpt[VatDetails](VatDetails.databaseFormat(crypto))
        registration              <- (json \ "registration").validateOpt[Registration](Registration.databaseFormat(crypto))
        dateOfBirthFromCid        <- (json \ "dateOfBirthFromCid").validateOpt[DateOfBirth](DateOfBirth.databaseFormat(crypto))
        clientCount               <- (json \ "clientCount").validateOpt[Int]
        lastNameFromCid = decryptOptString("lastNameFromCid", json)
        ctUtrCheckResult <- (json \ "ctUtrCheckResult").validateOpt[Boolean]
        isMAA            <- (json \ "isMAA").validateOpt[Boolean]
      } yield AgentSession(
        businessType,
        utr,
        postcode,
        nino,
        companyRegistrationNumber,
        dateOfBirth,
        registeredForVat,
        vatDetails,
        registration,
        dateOfBirthFromCid,
        clientCount,
        lastNameFromCid,
        ctUtrCheckResult,
        isMAA
      )

    def writes(agentSession: AgentSession): JsValue =
      Json.obj(
        "businessType"              -> agentSession.businessType,
        "utr"                       -> agentSession.utr.map(f => stringEncrypter.writes(f)),
        "postcode"                  -> agentSession.postcode.map(f => stringEncrypter.writes(f)),
        "nino"                      -> agentSession.nino.map(f => stringEncrypter.writes(f)),
        "companyRegistrationNumber" -> agentSession.companyRegistrationNumber,
        "dateOfBirth"               -> agentSession.dateOfBirth.map(f => DateOfBirth.databaseFormat.writes(f)),
        "registeredForVat"          -> agentSession.registeredForVat,
        "vatDetails"                -> agentSession.vatDetails.map(f => VatDetails.databaseFormat.writes(f)),
        "registration"              -> agentSession.registration.map(f => Registration.databaseFormat.writes(f)),
        "dateOfBirthFromCid"        -> agentSession.dateOfBirthFromCid.map(f => DateOfBirth.databaseFormat.writes(f)),
        "clientCount"               -> agentSession.clientCount,
        "lastNameFromCid"           -> agentSession.lastNameFromCid.map(f => stringEncrypter.writes(f)),
        "ctUtrCheckResult"          -> agentSession.ctUtrCheckResult,
        "isMAA"                     -> agentSession.isMAA
      )

    Format(reads(_), agentSession => writes(agentSession))
  }
  implicit val writes: Writes[AgentSession] = Json.writes[AgentSession]
  implicit val reads: Reads[AgentSession] = Json.reads[AgentSession]
}
