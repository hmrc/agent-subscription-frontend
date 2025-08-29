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

import uk.gov.hmrc.govukfrontend.views.Aliases.DateInput

object DateInputAutocompleteHelper {

  implicit class DateInputAutocomplete(dateInput: DateInput) extends DateInput {

    def withDateAutocomplete(autocomplete: Boolean): DateInput = {
      if (autocomplete) {
        val itemsWithAutocomplete = Seq(
          dateInput.items(0).copy(autocomplete = Some("bday-day")),
          dateInput.items(1).copy(autocomplete = Some("bday-month")),
          dateInput.items(2).copy(autocomplete = Some("bday-year"))
        )
        dateInput.copy(
          items = itemsWithAutocomplete
        )
      }
      else {
        dateInput
      }
    }
  }
}
