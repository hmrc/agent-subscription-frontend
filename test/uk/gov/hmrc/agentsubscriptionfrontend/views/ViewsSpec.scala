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

package uk.gov.hmrc.agentsubscriptionfrontend.views

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.views.html._
import uk.gov.hmrc.agentsubscriptionfrontend.support.UnitSpec

class ViewsSpec extends UnitSpec with GuiceOneAppPerSuite {

  "ErrorTemplate view" should {

    "render title, heading and message" in new App {
      val appConfig = app.injector.instanceOf[AppConfig]
      val messages = app.injector.instanceOf[Messages]

      val view = app.injector.instanceOf[ErrorTemplate]
      val html = view
        .render("My custom page title", "My custom heading", "My custom message", FakeRequest(), messages, appConfig)

      html.toString should {
        include("My custom page title") and
          include("My custom heading") and
          include("My custom message")
      }

      val hmtl2 =
        view.f("My custom page title", "My custom heading", "My custom message")(FakeRequest(), messages, appConfig)
      hmtl2 shouldBe html
    }
  }

  "main_template view" should {

    "render title, header, sidebar and main content" in new App {
      val view = app.injector.instanceOf[MainTemplate]
      val appConfig = app.injector.instanceOf[AppConfig]
      val messages = app.injector.instanceOf[Messages]
      val html = view.render(
        title = "My custom page title",
        userIsLoggedIn = true,
        hasTimeout = true,
        backLinkHref = Some("href"),
        mainContent = Html("mainContent"),
        request = FakeRequest(),
        messages = messages,
        appConfig = appConfig
      )

      html.toString should {
        include("My custom page title") and
          include("contentHeader") and
          include("type=\"text/javascript\"") and
          include("mainContent")
      }

      val html2 = view.f(
        "My custom page title",
        true,
        true,
        Some("href")
      )(Html("mainContent"))(FakeRequest(), messages, appConfig)
      html2 shouldBe html
    }

  }

}
