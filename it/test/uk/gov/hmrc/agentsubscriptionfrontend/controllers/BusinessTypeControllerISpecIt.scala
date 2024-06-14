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
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, AuthProviderId}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub.userIsAuthenticated
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{subscribingAgentEnrolledForNonMTD, subscribingCleanAgentWithoutEnrolments}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.validBusinessTypes
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestData, TestSetupNoJourneyRecord}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BusinessTypeControllerISpecIt extends BaseISpecIt with SessionDataMissingSpec {

  lazy val controller: BusinessTypeController = app.injector.instanceOf[BusinessTypeController]

  "redirectToBusinessTypeForm" should {
    "redirect to the business type form page" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.redirectToBusinessTypeForm(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }

  "showBusinessTypeForm (GET /business-type)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showBusinessTypeForm(_))

    behave like aPageTakingContinueUrlAndCachingInSessionStore(
      controller.showBusinessTypeForm(_),
      userIsAuthenticated(subscribingCleanAgentWithoutEnrolments)
    )

    "contain page titles and header content" in new TestSetupNoJourneyRecord {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))

      result should containMessages("businessType.title", "businessType.progressive.title", "businessType.progressive.content.p1")
    }

    "contain radio options for Sole Trader, Limited Company, Partnership, and LLP" in new TestSetupNoJourneyRecord {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))
      val doc = Jsoup.parse(bodyOf(result))

      // Check form's radio inputs have correct values
      doc.getElementById("businessType").`val`() shouldBe "limited_company"
      doc.getElementById("businessType-2").`val`() shouldBe "sole_trader"
      doc.getElementById("businessType-3").`val`() shouldBe "partnership"
      doc.getElementById("businessType-4").`val`() shouldBe "llp"
    }

    "contain a link to sign out" in new TestSetupNoJourneyRecord {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))

      status(result) shouldBe 200

      val html = Jsoup.parse(Helpers.contentAsString(Future.successful(result)))
      html.title() shouldBe "What type of business are you? - Create an agent services account - GOV.UK"
      private val signOutLink: Elements = html.select("a#sign-out")
      signOutLink.text() shouldBe "Finish and sign out"
      signOutLink.attr("href") shouldBe routes.SignedOutController.signOutWithContinueUrl().url
    }

    "pre-populate the business type if one is already stored in the session" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader))))

      val result = await(controller.showBusinessTypeForm()(request))

      val doc = Jsoup.parse(bodyOf(result))
      val link = doc.getElementById("businessType-2")
      link.hasAttr("checked") shouldBe true
    }

    "redirect to task list if a subscription journey exists for the logged in user" in {
      givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"), TestData.minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId")))
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
    }

    "redirect to task list if the agent has clean creds and is not partially subscribed" in {
      givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"), TestData.minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId")))
      val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      val result = await(controller.showBusinessTypeForm(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
    }
  }

  "submitBusinessTypeForm (POST /business-type)" when {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitBusinessTypeForm(_))

    validBusinessTypes.foreach { validBusinessTypeIdentifier =>
      s"redirect to /business-details when valid businessTypeIdentifier: $validBusinessTypeIdentifier" in new TestSetupNoJourneyRecord {
        val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
          .withFormUrlEncodedBody("businessType" -> validBusinessTypeIdentifier.key)

        val result = await(controller.submitBusinessTypeForm(request))
        result.header.headers(LOCATION) shouldBe routes.UtrController.showUtrForm().url
      }
    }

    "choice is invalid" should {
      "return 200 and redisplay the /business-type page with an error message for invalid choice - the user manipulated the submit value" in new TestSetupNoJourneyRecord {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("businessType" -> "invalid")
        val result = await(controller.submitBusinessTypeForm(request))
        result should containMessages("businessType.error.invalid-choice")
      }
    }
  }

}
