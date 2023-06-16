/*
 * Copyright 2023 HM Revenue & Customs
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

import java.time.LocalDate

import play.api.libs.json.{Json, OFormat}

case class EnterAMLSNumberForm(membershipNumber: String)

object EnterAMLSNumberForm {
  implicit val formatEnterAMLSNumberForm: OFormat[EnterAMLSNumberForm] = Json.format[EnterAMLSNumberForm]
}
case class EnterAMLSExpiryDateForm(expiry: LocalDate)
object EnterAMLSExpiryDateForm {
  implicit val formatAMLSExpiryDateForm: OFormat[EnterAMLSExpiryDateForm] = Json.format[EnterAMLSExpiryDateForm]
}

case class AMLSForm(amlsCode: String, membershipNumber: String, expiry: LocalDate)

object AMLSForm {
  implicit val formatAMLSForm: OFormat[AMLSForm] = Json.format[AMLSForm]
}

case class AmlsPendingForm(amlsCode: String, expiry: LocalDate)

object AmlsPendingForm {
  implicit val format: OFormat[AmlsPendingForm] = Json.format[AmlsPendingForm]
}
