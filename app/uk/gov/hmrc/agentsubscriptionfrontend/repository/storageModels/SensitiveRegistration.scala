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
import uk.gov.hmrc.agentsubscriptionfrontend.models.Registration
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive}

case class SensitiveRegistration(
  taxpayerName: Option[SensitiveString],
  isSubscribedToAgentServices: Boolean, // TODO remove?
  isSubscribedToETMP: Boolean, // TODO remove?
  address: SensitiveBusinessAddress,
  emailAddress: Option[SensitiveString],
  primaryPhoneNumber: Option[SensitiveString],
  safeId: Option[String]
) extends Sensitive[Registration] {
  def decryptedValue: Registration = Registration(
    taxpayerName.map(_.decryptedValue),
    isSubscribedToAgentServices,
    isSubscribedToETMP,
    address.decryptedValue,
    emailAddress.map(_.decryptedValue),
    primaryPhoneNumber.map(_.decryptedValue),
    safeId
  )
}

object SensitiveRegistration {
  def apply(registration: Registration): SensitiveRegistration = SensitiveRegistration(
    registration.taxpayerName.map(SensitiveString.apply),
    registration.isSubscribedToAgentServices,
    registration.isSubscribedToETMP,
    SensitiveBusinessAddress(registration.address),
    registration.emailAddress.map(SensitiveString.apply),
    registration.primaryPhoneNumber.map(SensitiveString.apply),
    registration.safeId
  )

  implicit def format(implicit crypto: Encrypter with Decrypter): Format[SensitiveRegistration] = {
    implicit val sensitiveStringFormat: Format[SensitiveString] =
      JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
    implicit val sensitiveBusinessAddressFormat: Format[SensitiveBusinessAddress] =
      SensitiveBusinessAddress.format
    Json.format[SensitiveRegistration]
  }
}
