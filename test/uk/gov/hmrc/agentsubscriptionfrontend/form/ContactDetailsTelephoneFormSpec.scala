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

package uk.gov.hmrc.agentsubscriptionfrontend.form

import uk.gov.hmrc.agentsubscriptionfrontend.controllers.ContactDetailsForms.{contactPhoneCheckForm, contactTelephoneForm}
import uk.gov.hmrc.agentsubscriptionfrontend.support.UnitSpec

class ContactDetailsTelephoneFormSpec extends UnitSpec {

  "contactPhoneCheckForm" should {
    "valid for yes/no answer" in {
      contactPhoneCheckForm.bind(Map("check" -> "yes")).hasErrors shouldBe false
      contactPhoneCheckForm.bind(Map("check" -> "no")).hasErrors shouldBe false
    }
    "invalid if empty" in {
      val form = contactPhoneCheckForm.bind(Map("check" -> ""))

      form.hasErrors shouldBe true
      form.errors.length shouldBe 1
      form.errors.head.message shouldBe "error.contact-phone-check.invalid"
    }
  }

  "contactTelephoneForm" should {
    "accept a valid UK number" in {
      contactTelephoneForm.bind(Map("telephone" -> "01273111111")).hasErrors shouldBe false
    }
    "invalid if empty" in {
      val form = contactTelephoneForm.bind(Map("telephone" -> ""))
      form.hasErrors shouldBe true
      form.errors.length shouldBe 1
      form.errors.head.message shouldBe "error.contact.phone.empty"
    }
    "invalid if not a UK telephone entered" in {
      val form = contactTelephoneForm.bind(Map("telephone" -> "0112121111111"))
      form.hasErrors shouldBe true
      form.errors.length shouldBe 1
      form.errors.head.message shouldBe "error.contact.phone.invalid"
    }
  }

}
