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

package uk.gov.hmrc.agentsubscriptionfrontend.util

import play.api.libs.json.Json
import uk.gov.hmrc.agentsubscriptionfrontend.support.UnitSpec
import uk.gov.hmrc.agentsubscriptionfrontend.util.EncryptionUtils
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter

import java.time.LocalDate

class EncryptionUtilsSpec extends UnitSpec {

  implicit val crypto: Encrypter with Decrypter = aesCrypto

  "EncryptionUtils" should {
    "decrypt an encrypted string" in {
      val secret = "my secret"
      val encrypted = stringEncrypter.writes(secret)
      val json = Json.obj("value" -> encrypted)
      EncryptionUtils.decryptString("value", json) shouldBe secret
    }
    "decrypt an encrypted optional string" in {
      val secret = "my secret"
      val encrypted = stringEncrypter.writes(secret)
      val json = Json.obj("value" -> encrypted)
      EncryptionUtils.decryptOptString("value", json) shouldBe Some(secret)
    }
    "decrypt an encrypted LocalDate" in {
      val secret: LocalDate = LocalDate.of(2020, 1, 1)
      val encrypted = stringEncrypter.writes(secret.toString)
      val json = Json.obj("value" -> encrypted)
      EncryptionUtils.decryptLocalDate("value", json) shouldBe secret
    }
  }
}
