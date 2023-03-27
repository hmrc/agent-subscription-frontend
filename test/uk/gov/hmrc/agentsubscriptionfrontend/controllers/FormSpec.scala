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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.ninoForm
import uk.gov.hmrc.agentsubscriptionfrontend.support.UnitSpec

class FormSpec extends UnitSpec {

  "Nino Form" should {
    "bind successfully" in {
      ninoForm.bind(Map("nino" -> "AB 82 21 21 B")).errors shouldBe List.empty
    }
    "handle lowercase characters with extra spaces" in {
      ninoForm.bind(Map("nino" -> " ab 82 21  21 b ")).errors shouldBe List.empty
    }
    "contain errors when invalid input" in {
      ninoForm.bind(Map("nino" -> " xbr ")).errors.nonEmpty shouldBe true
    }
    "contain errors when empty" in {
      ninoForm.bind(Map("nino" -> "  ")).errors.nonEmpty shouldBe true
    }
    "contain errors when invalid nino" in {
      ninoForm.bind(Map("nino" -> "XX123456X")).errors.nonEmpty shouldBe true
    }
  }
}
