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
import uk.gov.hmrc.agentsubscriptionfrontend.util.EncryptionUtils.decryptLocalDate
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

case class DateOfBirth(value: LocalDate)

object DateOfBirth {

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[DateOfBirth] = {
    def reads(json: JsValue): JsResult[DateOfBirth] = JsSuccess(DateOfBirth(decryptLocalDate("value", json)))

    def writes(dateOfBirth: DateOfBirth): JsValue =
      Json.obj(
        "value" -> stringEncrypter.writes(dateOfBirth.value.format(formatter))
      )

    Format(reads(_), dateOfBirth => writes(dateOfBirth))
  }

  implicit val format: Format[DateOfBirth] = new Format[DateOfBirth] {
    override def writes(o: DateOfBirth): JsValue =
      JsString(o.value.format(formatter))

    override def reads(json: JsValue): JsResult[DateOfBirth] =
      json match {
        case JsString(s) =>
          Try(LocalDate.parse(s, formatter)) match {
            case Success(date)  => JsSuccess(DateOfBirth(date))
            case Failure(error) => JsError(s"Could not parse date as yyyy-MM-dd: ${error.getMessage}")
          }

        case other => JsError(s"Expected string but got $other")
      }
  }
}
