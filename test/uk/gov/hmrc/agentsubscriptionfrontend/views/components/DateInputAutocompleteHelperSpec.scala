/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.views.components

import uk.gov.hmrc.agentsubscriptionfrontend.support.UnitSpec
import uk.gov.hmrc.agentsubscriptionfrontend.views.components.DateInputAutocompleteHelper.DateInputAutocomplete
import uk.gov.hmrc.govukfrontend.views.viewmodels.dateinput.{DateInput, InputItem}

class DateInputAutocompleteHelperSpec extends UnitSpec {

  val testDateInput: DateInput = DateInput(
    id = "inputId",
    items = Seq(
      InputItem(
        id = "dayId",
        name = "day"
      ),
      InputItem(
        id = "monthId",
        name = "month"
      ),
      InputItem(
        id = "yearId",
        name = "year"
      )
    )
  )

  ".withDateAutocomplete" when {

    "autocomplete is true" should {

      "copy the DateInput object and add the relevant 'bday' autocomplete attributes" in {
        val resultDateInput = testDateInput.withDateAutocomplete(true)
        resultDateInput.items(0).autocomplete shouldBe Some("bday-day")
        resultDateInput.items(1).autocomplete shouldBe Some("bday-month")
        resultDateInput.items(2).autocomplete shouldBe Some("bday-year")
      }
    }

    "autocomplete is false" should {

      "return the DateInput object without changes" in {
        val resultDateInput = testDateInput.withDateAutocomplete(false)
        resultDateInput shouldBe testDateInput
      }
    }
  }
}
