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

import java.time.LocalDate
import play.api.i18n.Lang
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, Cookie, RequestHeader, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AmlsDetails, _}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.{partialSubscriptionWillSucceed, withMatchingUtrAndPostcode}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionJourneyStub, AgentSubscriptionStub, AuthStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{individual, subscribingAgentEnrolledForNonMTD, subscribingCleanAgentWithoutEnrolments}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestData}
import uk.gov.hmrc.http.SessionKeys

class StartControllerISpecIt extends BaseISpecIt {

  protected lazy val controller: StartController = app.injector.instanceOf[StartController]
  protected lazy val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"

  private val id = AuthProviderId("12345-credId")
  private val continueId = ContinueId("foobar")
  private val record = TestData.minimalSubscriptionJourneyRecord(id)
  private lazy val rh: RequestHeader = FakeRequest()

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder

  object FixturesForReturnAfterGGCredsCreated {

    val amlsSDetails: AmlsDetails = AmlsDetails("supervisory", Some("123456789"), appliedOn = None, membershipExpiresOn = Some(LocalDate.now()))

    val agentSession: AgentSession =
      AgentSession(
        Some(BusinessType.SoleTrader),
        utr = Some(validUtr),
        postcode = Some(testPostcode),
        registration = Some(testRegistration)
      )

    trait UnsubscribedAgentStub {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(validUtr, testPostcode)
    }

    trait SubscribedAgentStub {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(validUtr, testPostcode, isSubscribedToAgentServices = true, isSubscribedToETMP = true)
    }

    trait PartiallySubscribedAgentStub {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(validUtr, testPostcode, isSubscribedToETMP = true)
      AgentSubscriptionJourneyStub.givenNoSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"))
    }

  }

  "context root" should {
    "redirect to start page" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      val result = await(controller.root(request))

      status(result) shouldBe 303
      redirectLocation(result).head should include(routes.StartController.start().url)
    }

    behave like anEndpointTakingContinueUrlAndRedirectingWithIt(controller.root(_))
  }

  "start" should {
    "redirect to the sign in check" in {
      AuthStub.userIsNotAuthenticated()

      val result = await(controller.start(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.signInCheck().url)
    }
  }

  "showNotAgent" when {
    "the current user is logged in" should {

      "display the non-agent next steps page" in {
        implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(individual)
        val result = await(controller.showNotAgent(request))

        status(result) shouldBe OK
        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")
        bodyOf(result) should include(htmlEscapedMessage("nonAgent.title"))
      }

      "include link to create new account" in {
        val result = await(controller.showNotAgent(authenticatedAs(individual)))

        status(result) shouldBe 200
        bodyOf(result) should include("/create-clean-creds")
      }
    }

    "the current user is not logged in" should {
      "redirect to the company-auth-frontend sign-in page" in {
        AuthStub.userIsNotAuthenticated()

        val request = FakeRequest()
        val result = await(controller.showNotAgent(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).head should include("bas-gateway/sign-in")
      }
    }

    behave like aPageWithFeedbackLinks(request => controller.showNotAgent(request), authenticatedAs(individual))
  }

  "signInCheck" when {
    "the current user is logged in" should {

      "display the sign in check page with correct links" in new SetupUnsubscribed {
        implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/agent-subscription/sign-in-check?continue=/go/somewhere")
          .withSession(SessionKeys.authToken -> "Bearer XYZ")
        val result: Result = await(controller.signInCheck(request))

        status(result) shouldBe OK
        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")
        bodyOf(result) should include(htmlEscapedMessage("sign-in-check.header"))
        result should containLink("sign-in-check.sign-out.link", routes.SignOutController.signOut().url)
        result should containLink("sign-in-check.create.link", routes.BusinessTypeController.showBusinessTypeForm().url)
        sessionStoreService.currentSession.continueUrl shouldBe Some("/go/somewhere")
      }

      "redirect to business type if the user has clean creds" in new SetupUnsubscribed {
        implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/agent-subscription/sign-in-check")
          .withSession(SessionKeys.authToken -> "Bearer XYZ")
        override implicit val authenticatedRequest: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        val result: Result = await(controller.signInCheck(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
      }
    }
  }

  trait SetupUnsubscribed {
    implicit val authenticatedRequest: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
    givenNoSubscriptionJourneyRecordExists(id)
    givenSubscriptionJourneyRecordExists(continueId, record.copy(continueId = Some(continueId.value)))
    givenSubscriptionRecordCreated(record.authProviderId, record.copy(continueId = Some(continueId.value)))
  }

  "returnAfterGGCredsCreated" should {

    "given a valid subscription journey record" when {

      "redirect to the /task-list page and update journey record with new clean creds id" in new SetupUnsubscribed {
        implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(SessionKeys.authToken -> "Bearer XYZ")

        val result: Result = await(controller.returnAfterGGCredsCreated(id = Some(continueId.value))(request))

        status(result) shouldBe 303
        redirectLocation(result).head should include(routes.TaskListController.showTaskList().url)
      }

      "redirect to the /task-list page when there is no continueId" in new SetupUnsubscribed {
        givenNoSubscriptionJourneyRecordExists(id)
        implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(SessionKeys.authToken -> "Bearer XYZ")

        val result: Result = await(controller.returnAfterGGCredsCreated()(request))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
      }
    }

    "complete partial subscription and redirect to complete when the user comes back as partially subscribed" in new SetupUnsubscribed {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = false, isSubscribedToETMP = true)
      partialSubscriptionWillSucceed(
        CompletePartialSubscriptionBody(validUtr, knownFacts = SubscriptionRequestKnownFacts(validPostcode), langForEmail = Some(Lang("en"))),
        arn = "TARN00023"
      )
      implicit val request: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest().withCookies(Cookie("PLAY_LANG", "en")).withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Result = await(controller.returnAfterGGCredsCreated(id = Some(continueId.value))(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.SubscriptionController.showSubscriptionComplete().url)
    }
  }

  "returnAfterMapping" should {

    "given a valid subscription journey record" when {

      "redirect to the /task-list page and update journey record with mappingComplete as true" in {
        implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        givenSubscriptionJourneyRecordExists(id, record)
        givenSubscriptionRecordCreated(record.authProviderId, record.copy(continueId = None, mappingComplete = true))

        val result = await(controller.returnAfterMapping()(request))

        status(result) shouldBe 303
        redirectLocation(result).head should include(routes.TaskListController.showTaskList().url)
      }

      "throw a runtime exception when there is no record" in {
        implicit val authenticatedRequest: FakeRequest[AnyContentAsEmpty.type] =
          authenticatedAs(subscribingAgentEnrolledForNonMTD)
        givenNoSubscriptionJourneyRecordExists(id)
        intercept[RuntimeException] {
          await(controller.returnAfterMapping()(authenticatedRequest))
        }.getMessage shouldBe "Expected Journey Record missing"
      }
    }
  }

  "GET /cannot-create-account" should {
    "display the cannot create account page" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

      val result = await(controller.showCannotCreateAccount()(request))

      status(result) shouldBe 200
      checkHtmlResultWithBodyText(
        result,
        "We could not confirm your identity",
        "Before you can create an agent services account, we need to be sure that a client has authorised you to deal with HMRC."
      )
    }
  }

}
