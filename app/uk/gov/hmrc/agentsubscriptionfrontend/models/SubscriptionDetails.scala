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

package uk.gov.hmrc.agentsubscriptionfrontend.models
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr

case class SubscriptionDetails(
  utr: Utr,
  knownFactsPostcode: String,
  name: String,
  email: String,
  address: DesAddress,
  amlsDetails: Option[AMLSDetails])

object SubscriptionDetails {
  implicit val formatDesAddress: Format[DesAddress] = Json.format[DesAddress]
  implicit val formatSubscriptionDetails: Format[SubscriptionDetails] = Json.format[SubscriptionDetails]

  implicit def mapper(
    initDetails: InitialDetails,
    address: DesAddress,
    mayBeAmlsDetails: Option[AMLSDetails]): SubscriptionDetails =
    SubscriptionDetails(
      initDetails.utr,
      initDetails.knownFactsPostcode,
      initDetails.name,
      initDetails.email.getOrElse(throw new Exception("email should not be empty")),
      address,
      mayBeAmlsDetails
    )
}
