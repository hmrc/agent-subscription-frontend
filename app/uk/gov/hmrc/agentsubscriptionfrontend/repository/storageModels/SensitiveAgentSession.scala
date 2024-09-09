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

package uk.gov.hmrc.agentsubscriptionfrontend.repository.storageModels

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive}

case class SensitiveAgentSession(
  businessType: Option[BusinessType] = None,
  utr: Option[SensitiveString] = None,
  postcode: Option[SensitiveString] = None,
  nino: Option[SensitiveString] = None,
  companyRegistrationNumber: Option[CompanyRegistrationNumber] = None,
  dateOfBirth: Option[SensitiveDateOfBirth] = None, // this is user entered DOB
  registeredForVat: Option[String] = None,
  vatDetails: Option[SensitiveVatDetails] = None,
  registration: Option[SensitiveRegistration] = None,
  dateOfBirthFromCid: Option[SensitiveDateOfBirth] = None,
  clientCount: Option[Int] = None,
  lastNameFromCid: Option[SensitiveString] = None,
  ctUtrCheckResult: Option[Boolean] = None,
  isMAA: Option[Boolean] = None
) extends Sensitive[AgentSession] {
  def decryptedValue: AgentSession = AgentSession(
    businessType,
    utr.map(_.decryptedValue),
    postcode.map(_.decryptedValue),
    nino.map(_.decryptedValue),
    companyRegistrationNumber,
    dateOfBirth.map(_.decryptedValue),
    registeredForVat,
    vatDetails.map(_.decryptedValue),
    registration.map(_.decryptedValue),
    dateOfBirthFromCid.map(_.decryptedValue),
    clientCount,
    lastNameFromCid.map(_.decryptedValue),
    ctUtrCheckResult,
    isMAA
  )
}

object SensitiveAgentSession {
  def apply(agentSession: AgentSession): SensitiveAgentSession = SensitiveAgentSession(
    agentSession.businessType,
    agentSession.utr.map(SensitiveString.apply),
    agentSession.postcode.map(SensitiveString.apply),
    agentSession.nino.map(SensitiveString.apply),
    agentSession.companyRegistrationNumber,
    agentSession.dateOfBirth.map(SensitiveDateOfBirth.apply),
    agentSession.registeredForVat,
    agentSession.vatDetails.map(SensitiveVatDetails.apply),
    agentSession.registration.map(SensitiveRegistration.apply),
    agentSession.dateOfBirthFromCid.map(SensitiveDateOfBirth.apply),
    agentSession.clientCount,
    agentSession.lastNameFromCid.map(SensitiveString.apply),
    agentSession.ctUtrCheckResult,
    agentSession.isMAA
  )

  implicit def format(implicit crypto: Encrypter with Decrypter): Format[SensitiveAgentSession] = {
    implicit val sensitiveRegistrationFormat: Format[SensitiveRegistration] = SensitiveRegistration.format
    implicit val sensitiveVatDetailsFormat: Format[SensitiveVatDetails] = SensitiveVatDetails.format
    implicit val sensitiveDateOfBirthFormat: Format[SensitiveDateOfBirth] = SensitiveDateOfBirth.format
    implicit val sensitiveStringFormat: Format[SensitiveString] =
      JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
    Json.format[SensitiveAgentSession]
  }

}
