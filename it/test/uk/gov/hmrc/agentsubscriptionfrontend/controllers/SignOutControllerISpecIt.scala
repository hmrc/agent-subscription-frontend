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

import java.net.URLEncoder
import org.scalatest.Assertion
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sttp.model.Uri.UriContext
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.CallOps.addParamsToUrl
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, SessionLost, TestData}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl

import scala.concurrent.ExecutionContext.Implicits.global

class SignOutControllerISpecIt extends BaseISpecIt {

  protected lazy val sosRedirectUrl = "/government-gateway-registration-frontend"
  protected lazy val controller: SignOutController = app.injector.instanceOf[SignOutController]
  private val signOutUrl: String = uri"http://localhost:9099/bas-gateway/sign-out-without-state".toString

  private val fakeRequest = FakeRequest()

  trait TestSetup {
    givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(continueId = Some("foo")))
  }

  def signOutWithContinue(continue: String): String =
    uri"""$signOutUrl?${Map("continue" -> continue)}""".toString

  "redirectAgentToCreateCleanCreds" should {

    "redirect user to create clean creds" in new TestSetup {
      private val result = controller
        .redirectAgentToCreateCleanCreds(authenticatedAs(subscribingAgentEnrolledForNonMTD))
        .futureValue

      status(result) shouldBe 303
      redirectLocation(result).head should include(sosRedirectUrl)
    }

    "the SOS redirect URL should include an ID of the saved continue id" in new TestSetup {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val continueFromGG = uri"${controller.appConfig.returnAfterGGCredsCreatedUrl}?${Map("id" -> "foo")}"
      val expectedLocation = uri"${controller.appConfig.ggRegistrationFrontendExternalUrl}?${Map(
          "accountType" -> "agent",
          "origin"      -> "unknown",
          "continue"    -> continueFromGG.toString
        )}"
      private val result = controller.redirectAgentToCreateCleanCreds(request).futureValue
      redirectLocation(result).head shouldBe signOutWithContinue(expectedLocation.toString)
    }

    def assertContinueUrl(result: Result, continueUrl: String): Assertion = {
      val continue = uri"${controller.appConfig.returnAfterGGCredsCreatedUrl}?${Map("continue" -> continueUrl)}"
      redirectLocation(result).head shouldBe signOutWithContinue(continue.toString)
    }

    "include a continue URL in the SOS redirect URL if a continue URL exists in the session store" in {

      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(continueId = None))

      val ourContinueUrl = "/test-continue-url"
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.continueUrl = Some(ourContinueUrl)
      val continueFromGG = uri"${controller.appConfig.returnAfterGGCredsCreatedUrl}?${Map("continue" -> ourContinueUrl)}"
      val expectedLocation = uri"${controller.appConfig.ggRegistrationFrontendExternalUrl}?${Map(
          "accountType" -> "agent",
          "origin"      -> "unknown",
          "continue"    -> continueFromGG.toString
        )}"
      val result = controller.redirectAgentToCreateCleanCreds(request).futureValue
      redirectLocation(result).head shouldBe signOutWithContinue(expectedLocation.toString)
    }

    "include both an ID and a continue URL in the SOS redirect URL if both a continue URL and KnownFacts exist in the session store" in new TestSetup {
      val ourContinueUrl = "/test-continue-url"
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.continueUrl = Some(ourContinueUrl)
      private val result = controller.redirectAgentToCreateCleanCreds(request).futureValue
      val continue = uri"${controller.appConfig.returnAfterGGCredsCreatedUrl}?${Map(
          "id"       -> "foo",
          "continue" -> ourContinueUrl
        )}"
      val expectedLocation = uri"${controller.appConfig.ggRegistrationFrontendExternalUrl}?${Map(
          "accountType" -> "agent",
          "origin"      -> "unknown",
          "continue"    -> continue.toString
        )}"
      redirectLocation(result).head shouldBe signOutWithContinue(expectedLocation.toString)
    }
  }

  "startSurvey" should {
    "redirect to the survey page" in {
      val result = await(controller.startSurvey(fakeRequest))

      status(result) shouldBe 303
      redirectLocation(result).head should include("feedback/AGENTSUB")
    }
  }

  "redirectToASAccountPage" should {
    "logout and redirect to agent services account" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest.withSession("sessionId" -> "SomeSession")
      request.session.get("sessionId") should not be empty
      val result = await(controller.redirectToASAccountPage(request))
      status(result) shouldBe 303
      redirectLocation(result).head should include("agent-services-account")
    }
  }

  "signOutWithContinueUrl" should {

    "sign out via bas-gateway-frontend to continueUrl with expired session should just sign out; not give an exception" in {

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest.withSession("sessionId" -> "SomeSession")
      val expectedLocation = uri"$signOutUrl?${Map("continue" -> routes.TaskListController.showTaskList().url)}"
      sessionStoreService.cacheContinueUrl(RedirectUrl("/someContinueUrl"))

      request.session.get("sessionId") should not be empty

      sessionStoreService.currentSessionTest = SessionLost // simulate SessionCache expiry

      val result = controller.signOutWithContinueUrl(fakeRequest).futureValue

      status(result) shouldBe 303
      redirectLocation(result).head shouldBe expectedLocation.toString

    }

  }

  "signOut" should {
    "redirect via bas-gateway-frontend to task list page" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest.withSession("sessionId" -> "SomeSession")
      val expectedLocation = uri"$signOutUrl?${Map("continue" -> routes.TaskListController.showTaskList().url)}"
      val result = controller.signOut(request).futureValue

      status(result) shouldBe 303
      redirectLocation(result).head shouldBe expectedLocation.toString
    }
  }

  "timeOut" should {
    "redirect via bas-gateway-frontend to the timed out page" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest.withSession("sessionId" -> "SomeSession")
      val continueUrl = uri"${controller.appConfig.selfExternalUrl + routes.SignOutController.timedOut().url}"
      val expectedLocation = uri"""$signOutUrl?${Map("continue" -> continueUrl.toString)}"""
      val result = controller.timeOut(request).futureValue

      status(result) shouldBe 303
      redirectLocation(result).head shouldBe expectedLocation.toString
    }
  }

  "timedOut" should {
    "show the timed out page" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest
      val result = controller.timedOut(request).futureValue
      status(result) shouldBe 200
      checkHtmlResultWithBodyText(result, "You have been signed out", "Sign in again")
    }
  }

  "redirectToBusinessTypeForm" should {
    "redirect via bas-gateway-frontend to the business type page" in {
      val expectedLocation = uri"""$signOutUrl?${Map("continue" -> routes.BusinessTypeController.showBusinessTypeForm().url)}"""
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest.withSession("sessionId" -> "SomeSession")
      val result = controller.redirectToBusinessTypeForm(request).futureValue

      status(result) shouldBe 303

      redirectLocation(result).head shouldBe expectedLocation.toString
    }
  }
}
