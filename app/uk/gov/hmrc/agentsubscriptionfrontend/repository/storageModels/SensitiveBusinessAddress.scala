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
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessAddress
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive}

case class SensitiveBusinessAddress(
  addressLine1: SensitiveString,
  addressLine2: Option[SensitiveString],
  addressLine3: Option[SensitiveString] = None,
  addressLine4: Option[SensitiveString] = None,
  postalCode: Option[SensitiveString],
  countryCode: SensitiveString
) extends Sensitive[BusinessAddress] {
  def decryptedValue: BusinessAddress = BusinessAddress(
    addressLine1.decryptedValue,
    addressLine2.map(_.decryptedValue),
    addressLine3.map(_.decryptedValue),
    addressLine4.map(_.decryptedValue),
    postalCode.map(_.decryptedValue),
    countryCode.decryptedValue
  )
}

object SensitiveBusinessAddress {
  def apply(businessAddress: BusinessAddress): SensitiveBusinessAddress = SensitiveBusinessAddress(
    SensitiveString(businessAddress.addressLine1),
    businessAddress.addressLine2.map(SensitiveString.apply),
    businessAddress.addressLine3.map(SensitiveString.apply),
    businessAddress.addressLine4.map(SensitiveString.apply),
    businessAddress.postalCode.map(SensitiveString.apply),
    SensitiveString(businessAddress.countryCode)
  )

  implicit def format(implicit crypto: Encrypter with Decrypter): Format[SensitiveBusinessAddress] = {
    implicit val sensitiveStringFormat: Format[SensitiveString] =
      JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
    Json.format[SensitiveBusinessAddress]
  }

}
