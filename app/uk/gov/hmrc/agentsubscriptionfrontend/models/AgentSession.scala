/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.domain.Nino

/**
  * Holds data about a non-MTD agent's initial onboarding session before they have a Journey Subscription Record
  * Just holds data about the business identification step, which occurs before the record is created.
  */
case class AgentSession(
  businessType: Option[BusinessType] = None,
  utr: Option[Utr] = None,
  postcode: Option[Postcode] = None,
  nino: Option[Nino] = None,
  companyRegistrationNumber: Option[CompanyRegistrationNumber] = None,
  dateOfBirth: Option[DateOfBirth] = None, // this is user entered DOB
  registeredForVat: Option[String] = None,
  vatDetails: Option[VatDetails] = None,
  registration: Option[Registration] = None,
  dateOfBirthFromCid: Option[DateOfBirth] = None, //just caching this dob from CID so we dont need to make multiple calls to CID
  clientCount: Option[Int] = None,
  lastNameFromCid: Option[String] = None,
  ctUtrCheckResult: Option[Boolean] = None,
  isMAA: Option[Boolean] = None
)

object AgentSession {
  implicit val format: Format[AgentSession] = Json.format[AgentSession]
}
