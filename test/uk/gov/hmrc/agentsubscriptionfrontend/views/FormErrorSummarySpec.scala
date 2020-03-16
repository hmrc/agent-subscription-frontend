/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.views

import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{DefaultMessagesApi, Messages}
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.play.test.UnitSpec

class FormErrorSummarySpec extends UnitSpec {

  private case class Model(name: String)

  private val maxLength = 9

  private val testForm = Form[Model](mapping("name" -> text(maxLength = maxLength))(Model.apply)(Model.unapply))

  val messagesApi = new DefaultMessagesApi(
    Map(
      "en" ->
        Map("error.min" -> "minimum!")
    )
  )
  implicit val request = {
    FakeRequest("POST", "/")
      .withFormUrlEncodedBody("name" -> "Play", "age" -> "-1")
  }
  implicit val messages = messagesApi.preferred(request)

  "form_error_summary" should {
    "display error messages.en including arguments" in {

      val formWithError = testForm.bind(Map("name" -> "too long too long"))
      val errorView = new uk.gov.hmrc.play.views.html.helpers.ErrorSummary
      errorView("heading", formWithError).toString should include(htmlEscapedMessage("error.maxLength", maxLength))
    }
  }

  protected def htmlEscapedMessage(key: String, args: Any*): String =
    HtmlFormat.escape(Messages(key, args: _*)).toString
}
