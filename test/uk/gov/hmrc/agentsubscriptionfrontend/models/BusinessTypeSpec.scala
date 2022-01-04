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

import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType._
import uk.gov.hmrc.agentsubscriptionfrontend.support.UnitSpec

class BusinessTypeSpec extends UnitSpec {

  "BusinessType" should {
    "serialize to json string for each business type" in {
      Json.toJson[BusinessType](SoleTrader) shouldBe JsString("sole_trader")
      Json.toJson[BusinessType](LimitedCompany) shouldBe JsString("limited_company")
      Json.toJson[BusinessType](Partnership) shouldBe JsString("partnership")
      Json.toJson[BusinessType](Llp) shouldBe JsString("llp")

    }
    "deserialize from json string for each business type" in {
      JsString("sole_trader").as[BusinessType] shouldBe SoleTrader
      JsString("limited_company").as[BusinessType] shouldBe LimitedCompany
      JsString("partnership").as[BusinessType] shouldBe Partnership
      JsString("llp").as[BusinessType] shouldBe Llp
    }
  }
}
