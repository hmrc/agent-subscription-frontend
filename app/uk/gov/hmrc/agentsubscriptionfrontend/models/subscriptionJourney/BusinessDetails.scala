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

package uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney

import play.api.libs.json._
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.util.EncryptionUtils.{decryptOptString, decryptString}
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

/** Information about the agent's business. They must always provide a business type, UTR and postcode. But other data points are only required for
  * some business types and if certain conditions are NOT met e.g. if they provide a NINO, they must provide date of birth if they are registered for
  * vat, they must provide vat details The record is created once we have the minimum business details
  */
case class BusinessDetails(
  businessType: BusinessType,
  utr: String, // CT or SA
  postcode: String,
  registration: Option[Registration] = None,
  nino: Option[String] = None,
  companyRegistrationNumber: Option[CompanyRegistrationNumber] = None,
  dateOfBirth: Option[DateOfBirth] = None, // if NINO required
  registeredForVat: Option[Boolean] = None,
  vatDetails: Option[VatDetails] = None, // if registered for VAT
  encrypted: Option[Boolean] = None
)

object BusinessDetails {

  def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[BusinessDetails] = {

    def reads(json: JsValue): JsResult[BusinessDetails] =
      for {
        isEncrypted  <- (json \ "encrypted").validateOpt[Boolean]
        businessType <- (json \ "businessType").validate[BusinessType]
        utr = decryptString("utr", isEncrypted, json)
        postcode = decryptString("postcode", isEncrypted, json)
        registration <- (json \ "registration").validateOpt[Registration](Registration.databaseFormat(crypto))
        nino = decryptOptString("nino", isEncrypted, json)
        companyRegistrationNumber <- (json \ "companyRegistrationNumber").validateOpt[CompanyRegistrationNumber]
        dateOfBirth               <- (json \ "dateOfBirth").validateOpt[DateOfBirth]
        registeredForVat          <- (json \ "registeredForVat").validateOpt[Boolean]
        vatDetails                <- (json \ "vatDetails").validateOpt[VatDetails]
      } yield BusinessDetails(
        businessType,
        utr,
        postcode,
        registration,
        nino,
        companyRegistrationNumber,
        dateOfBirth,
        registeredForVat,
        vatDetails,
        isEncrypted
      )

    def writes(businessDetails: BusinessDetails): JsValue =
      Json.obj(
        "businessType"              -> businessDetails.businessType,
        "utr"                       -> stringEncrypter.writes(businessDetails.utr),
        "postcode"                  -> stringEncrypter.writes(businessDetails.postcode),
        "registration"              -> businessDetails.registration.map(Registration.databaseFormat.writes),
        "nino"                      -> businessDetails.nino.map(stringEncrypter.writes),
        "companyRegistrationNumber" -> businessDetails.companyRegistrationNumber,
        "dateOfBirth"               -> businessDetails.dateOfBirth,
        "registeredForVat"          -> businessDetails.registeredForVat,
        "vatDetails"                -> businessDetails.vatDetails,
        "encrypted"                 -> true
      )

    Format(reads(_), businessDetails => writes(businessDetails))
  }

  implicit val format: Format[BusinessDetails] = Json.format[BusinessDetails]
}
