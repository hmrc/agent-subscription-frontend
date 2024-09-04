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

package uk.gov.hmrc.agentsubscriptionfrontend.models

import play.api.libs.json._
import uk.gov.hmrc.agentsubscriptionfrontend.util.EncryptionUtils.{decryptOptString, decryptString}
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

case class BusinessAddress(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  postalCode: Option[String],
  countryCode: String,
  encrypted: Option[Boolean] = None
)

object BusinessAddress {
  def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[BusinessAddress] = {

    def reads(json: JsValue): JsResult[BusinessAddress] =
      for {
        isEncrypted <- (json \ "encrypted").validateOpt[Boolean]
        addressLine1 = decryptString("addressLine1", isEncrypted, json)
        addressLine2 = decryptOptString("addressLine2", isEncrypted, json)
        addressLine3 = decryptOptString("addressLine3", isEncrypted, json)
        addressLine4 = decryptOptString("addressLine4", isEncrypted, json)
        postalCode = decryptOptString("postalCode", isEncrypted, json)
        countryCode = decryptString("countryCode", isEncrypted, json)
      } yield BusinessAddress(
        addressLine1,
        addressLine2,
        addressLine3,
        addressLine4,
        postalCode,
        countryCode,
        isEncrypted
      )

    def writes(businessAddress: BusinessAddress): JsValue =
      Json.obj(
        "addressLine1" -> stringEncrypter.writes(businessAddress.addressLine1),
        "addressLine2" -> businessAddress.addressLine2.map(stringEncrypter.writes),
        "addressLine3" -> businessAddress.addressLine3.map(stringEncrypter.writes),
        "addressLine4" -> businessAddress.addressLine4.map(stringEncrypter.writes),
        "postalCode"   -> businessAddress.postalCode.map(stringEncrypter.writes),
        "countryCode"  -> stringEncrypter.writes(businessAddress.countryCode),
        "encrypted"    -> Some(true)
      )

    Format(reads(_), businessAddress => writes(businessAddress))
  }

  implicit val format: Format[BusinessAddress] = Json.format[BusinessAddress]

  def apply(desAddress: DesAddress): BusinessAddress =
    BusinessAddress(
      desAddress.addressLine1,
      desAddress.addressLine2,
      desAddress.addressLine3,
      desAddress.addressLine4,
      Some(desAddress.postcode),
      desAddress.countryCode
    )
}

case class Registration(
  taxpayerName: Option[String],
  isSubscribedToAgentServices: Boolean, // TODO remove?
  isSubscribedToETMP: Boolean, // TODO remove?
  address: BusinessAddress,
  emailAddress: Option[String],
  primaryPhoneNumber: Option[String],
  safeId: Option[String],
  encrypted: Option[Boolean] = None
)

object Registration {
  def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[Registration] = {

    def reads(json: JsValue): JsResult[Registration] =
      for {
        isEncrypted <- (json \ "encrypted").validateOpt[Boolean]
        taxpayerName = decryptOptString("taxpayerName", isEncrypted, json)
        isSubscribedToAgentServices = (json \ "isSubscribedToAgentServices").as[Boolean]
        isSubscribedToETMP = (json \ "isSubscribedToETMP").as[Boolean]
        address = (json \ "address").as[BusinessAddress](BusinessAddress.databaseFormat(crypto))
        emailAddress = decryptOptString("emailAddress", isEncrypted, json)
        primaryPhoneNumber = decryptOptString("primaryPhoneNumber", isEncrypted, json)
        safeId = (json \ "safeId").asOpt[String]
      } yield Registration(
        taxpayerName,
        isSubscribedToAgentServices,
        isSubscribedToETMP,
        address,
        emailAddress,
        primaryPhoneNumber,
        safeId,
        isEncrypted
      )

    def writes(registration: Registration): JsValue =
      Json.obj(
        "taxpayerName"                -> registration.taxpayerName.map(stringEncrypter.writes),
        "isSubscribedToAgentServices" -> registration.isSubscribedToAgentServices,
        "isSubscribedToETMP"          -> registration.isSubscribedToETMP,
        "address"                     -> BusinessAddress.databaseFormat.writes(registration.address),
        "emailAddress"                -> registration.emailAddress.map(stringEncrypter.writes),
        "primaryPhoneNumber"          -> registration.primaryPhoneNumber.map(stringEncrypter.writes),
        "safeId"                      -> registration.safeId,
        "encrypted"                   -> Some(true)
      )
    Format(reads(_), registration => writes(registration))
  }

  implicit val writes: Writes[Registration] = Json.writes[Registration]
  implicit val reads: Reads[Registration] = Json.reads[Registration]
}

case class UpdateBusinessAddressForm(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  postCode: String
)

object UpdateBusinessAddressForm {
  def apply(businessAddress: BusinessAddress): UpdateBusinessAddressForm =
    UpdateBusinessAddressForm(
      businessAddress.addressLine1,
      businessAddress.addressLine2,
      businessAddress.addressLine3,
      businessAddress.addressLine4,
      businessAddress.postalCode.getOrElse(throw new Exception("Postcode is mandatory"))
    )
}
