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

package uk.gov.hmrc.agentsubscriptionfrontend.form

import org.scalatestplus.mockito.MockitoSugar
import play.api.data.validation.{Invalid, Valid, ValidationError}
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.AMLSForms._
import uk.gov.hmrc.agentsubscriptionfrontend.models.EnterAMLSNumberForm
import uk.gov.hmrc.agentsubscriptionfrontend.support.UnitSpec

class AmlsFormSpecIt extends UnitSpec with MockitoSugar {

  "membershipNumberConstraint" should {
    "return Valid for a valid membership number" in {
      val result = membershipNumberConstraint("XAML00000100000")
      result shouldBe Valid
    }

    "return Invalid with HMRC_AMLS_Empty_ERROR for an empty membership number" in {
      val result = membershipNumberConstraint("")
      result shouldBe Invalid(Seq(ValidationError(HMRC_AMLS_Empty_ERROR)))
    }

    "return Invalid with HMRC_AMLS_ERROR for an invalid membership number" in {
      val result = membershipNumberConstraint("1234567890!")
      result shouldBe Invalid(Seq(ValidationError(HMRC_AMLS_ERROR)))
    }
  }

  "amlsEnterNumberForm" should {
    "bind data correctly when valid membership number is provided" in {
      val formData = Map("membershipNumber" -> "XAML00000100000")
      val form = amlsEnterNumberForm().bind(formData)
      form.hasErrors shouldBe false
      form.value shouldBe Some(EnterAMLSNumberForm("XAML00000100000"))
    }

    "have errors when an empty membership number is provided" in {
      val formData = Map("membershipNumber" -> "")
      val form = amlsEnterNumberForm().bind(formData)
      form.hasErrors shouldBe true
      form.errors.head.message shouldBe HMRC_AMLS_Empty_ERROR
    }

    "have errors when an invalid membership number is provided" in {
      val formData = Map("membershipNumber" -> "1234567890!")
      val form = amlsEnterNumberForm().bind(formData)
      form.hasErrors shouldBe true
      form.errors.head.message shouldBe HMRC_AMLS_ERROR
    }
  }
}
