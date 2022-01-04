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
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.AmlsData

case class SubscriptionDetails(utr: Utr, knownFactsPostcode: String, name: String, email: String, address: DesAddress, amlsData: Option[AmlsData])

object SubscriptionDetails {
  implicit val formatDesAddress: Format[DesAddress] = Json.format[DesAddress]
  implicit val formatSubscriptionDetails: Format[SubscriptionDetails] = Json.format[SubscriptionDetails]

  implicit def mapper(utr: Utr, postcode: Postcode, agency: Agency, amlsData: Option[AmlsData]): SubscriptionDetails = {
    val desAddress = agency.address

    SubscriptionDetails(
      utr,
      postcode.value,
      agency.name,
      agency.email,
      desAddress,
      amlsData
    )
  }
}
