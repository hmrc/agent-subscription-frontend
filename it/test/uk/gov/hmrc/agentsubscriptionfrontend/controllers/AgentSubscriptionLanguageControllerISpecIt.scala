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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestData}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub.givenAgentIsNotManuallyAssured
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.givenSubscriptionJourneyRecordExists
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingCleanAgentWithoutEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.id
import play.api.test.Helpers
import play.api.test.Helpers._

import scala.concurrent.duration._

class AgentSubscriptionLanguageControllerISpecIt extends BaseISpecIt {

  lazy private val controller: AgentSubscriptionLanguageController = app.injector.instanceOf[AgentSubscriptionLanguageController]

  implicit val timeout: FiniteDuration = 2.seconds

  val utr = Utr("2000000000")
  trait Setup {
    implicit val authenticatedRequest: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
    givenAgentIsNotManuallyAssured(utr.value)
    givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecord(id))
  }

  "GET /language/:lang" should {

    val request = FakeRequest("GET", "/language/english")

    "redirect to https://www.tax.service.co.uk/agent-subscription/start when the request header contains no referer" in {

      val result = controller.switchToLanguage("english")(request)
      status(result) shouldBe 303
      Helpers.redirectLocation(result)(timeout) shouldBe Some("https://www.tax.service.gov.uk/agent-subscription/start")

      cookies(result)(timeout).get("PLAY_LANG").get.value shouldBe "en"
    }

    "redirect to /some-page when the request header contains referer /some-page" in {

      val request = FakeRequest("GET", "/language/english").withHeaders("referer" -> "/some-page")

      val result = controller.switchToLanguage("english")(request)
      status(result) shouldBe 303
      Helpers.redirectLocation(result)(timeout) shouldBe Some("/some-page")

      cookies(result)(timeout).get("PLAY_LANG").get.value shouldBe "en"

    }

    "redirect to /check-money-laundering-compliance with welsh language when toggle pressed on that page" in {

      val request = FakeRequest("GET", "/language/english").withHeaders("referer" -> "/check-money-laundering-compliance")

      val result = controller.switchToLanguage("cymraeg")(request)
      status(result) shouldBe 303
      Helpers.redirectLocation(result)(timeout) shouldBe Some("/check-money-laundering-compliance")

      cookies(result)(timeout).get("PLAY_LANG").get.value shouldBe "cy"
    }
  }
}
