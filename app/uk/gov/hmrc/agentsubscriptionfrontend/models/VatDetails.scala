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
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agentsubscriptionfrontend.models.Vrn
import uk.gov.hmrc.agentsubscriptionfrontend.util.EncryptionUtils.decryptLocalDate
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.LocalDate

case class VatDetails(vrn: Vrn, regDate: LocalDate)

object VatDetails {
  def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[VatDetails] = {
    def reads(json: play.api.libs.json.JsValue): play.api.libs.json.JsResult[VatDetails] =
      for {
        vrn <- (json \ "vrn").validate[Vrn]
        regDate = decryptLocalDate("regDate", json)
      } yield VatDetails(vrn, regDate)

    def writes(vatDetails: VatDetails): play.api.libs.json.JsValue =
      Json.obj(
        "vrn"     -> vatDetails.vrn,
        "regDate" -> stringEncrypter.writes(vatDetails.regDate.toString)
      )

    Format(reads(_), vatDetails => writes(vatDetails))
  }
  implicit val format: Format[VatDetails] = Json.format[VatDetails]
}
