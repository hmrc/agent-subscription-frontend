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

case class DesAddress(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String],
  addressLine4: Option[String],
  postcode: String,
  countryCode: String)

object DesAddress {

  def fromBusinessAddress(businessAddress: BusinessAddress): DesAddress =
    DesAddress(
      addressLine1 = businessAddress.addressLine1,
      addressLine2 = businessAddress.addressLine2,
      addressLine3 = businessAddress.addressLine3,
      addressLine4 = businessAddress.addressLine4,
      postcode = businessAddress.postalCode.getOrElse(throw new NullPointerException("postal code should be defined")),
      countryCode = businessAddress.countryCode
    )
}
