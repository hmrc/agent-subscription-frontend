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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agentsubscriptionfrontend.models.FormBundleStatus.FormBundleStatus

import java.time.LocalDate

object FormBundleStatus extends Enumeration {

  type FormBundleStatus = Value

  val None = Value("None")
  val Pending = Value("Pending")
  val Withdrawal = Value("Withdrawal")
  val Approved = Value("Approved")
  val ApprovedWithConditions = Value("ApprovedWithConditions")
  val Rejected = Value("Rejected")
  val RejectedUnderReviewAppeal = Value("Rejected under Review/Appeal")
  val Revoked = Value("Revoked")
  val RevokedUnderReviewAppeal = Value("Revoked under Review/Appeal")
  val DeRegistered = Value("De-Registered")
  val Expired = Value("Expired")

  implicit val format: Format[FormBundleStatus] = Json.formatEnum(FormBundleStatus)

}

case class AmlsSubscriptionRecord(
  formBundleStatus: FormBundleStatus,
  safeId: String,
  currentRegYearStartDate: Option[LocalDate],
  currentRegYearEndDate: Option[LocalDate],
  suspended: Option[Boolean],
)

object AmlsSubscriptionRecord {
  implicit val amlsSubscriptionRecordFormat = Json.format[AmlsSubscriptionRecord]
}
