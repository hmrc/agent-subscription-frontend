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

import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.support.UnitSpec
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString

import java.time.LocalDate

class SensitiveAgentSessionSpec extends UnitSpec {

  private val vatDetails = VatDetails(Vrn("123456789"), regDate = LocalDate.of(2020, 1, 1))
  private val dob = DateOfBirth(LocalDate.of(1990, 1, 1))
  private val registration = Registration(
    Some("ACME"),
    isSubscribedToAgentServices = false,
    isSubscribedToETMP = true,
    BusinessAddress(
      "1 ACME Street",
      Some("ACME Town"),
      None,
      None,
      Some("AC1 1AA"),
      "GB"
    ),
    Some("abc@example.com"),
    Some("0123456789"),
    Some("X00000000000000")
  )

  private val sensitiveDob = SensitiveDateOfBirth(dob)
  private val sensitiveVatDetails = SensitiveVatDetails(vatDetails)
  private val sensitiveRegistration = SensitiveRegistration(registration)

  val sensitiveAgentSession: SensitiveAgentSession = SensitiveAgentSession(
    businessType = Some(BusinessType.SoleTrader),
    utr = Some(SensitiveString("utr")),
    postcode = Some(SensitiveString("postcode")),
    nino = Some(SensitiveString("nino")),
    companyRegistrationNumber = Some(CompanyRegistrationNumber("crn")),
    dateOfBirth = Some(sensitiveDob),
    registeredForVat = Some("yes"),
    vatDetails = Some(sensitiveVatDetails),
    registration = Some(sensitiveRegistration),
    dateOfBirthFromCid = Some(sensitiveDob),
    clientCount = Some(1),
    lastNameFromCid = Some(SensitiveString("lastname")),
    ctUtrCheckResult = Some(true),
    isMAA = Some(true)
  )

  "SensitiveAgentSession" should {

    "serialise and deserialise correctly" in {
      implicit val crypto: Encrypter with Decrypter = aesCrypto
      val json = Json.toJson(sensitiveAgentSession)
      val deserialised: SensitiveAgentSession = Json.fromJson[SensitiveAgentSession](json).get
      deserialised shouldBe sensitiveAgentSession
    }

    "decrypt the sensitive data" in {

      val agentSession = sensitiveAgentSession.decryptedValue

      agentSession shouldBe AgentSession(
        businessType = Some(BusinessType.SoleTrader),
        utr = Some("utr"),
        postcode = Some("postcode"),
        nino = Some("nino"),
        companyRegistrationNumber = Some(CompanyRegistrationNumber("crn")),
        dateOfBirth = Some(dob),
        registeredForVat = Some("yes"),
        vatDetails = Some(vatDetails),
        registration = Some(registration),
        dateOfBirthFromCid = Some(dob),
        clientCount = Some(1),
        lastNameFromCid = Some("lastname"),
        ctUtrCheckResult = Some(true),
        isMAA = Some(true)
      )
    }
  }
}
