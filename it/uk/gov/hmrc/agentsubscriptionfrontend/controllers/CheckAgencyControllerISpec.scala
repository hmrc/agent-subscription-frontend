/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._

class CheckAgencyControllerISpec extends BaseISpec with SessionDataMissingSpec {
  val validUtr = Utr("2000000000")
  val validPostcode = "AA1 1AA"
  private val invalidPostcode = "not a postcode"

  val utr = Utr("0123456789")
  val postcode = "AA11AA"
  val registrationName = "My Agency"

  private lazy val redirectUrl: String = "http://localhost:9401/agent-services-account"

  private lazy val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"

  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder
    .configure("government-gateway.url" -> configuredGovernmentGatewayUrl)

  lazy val controller: CheckAgencyController = app.injector.instanceOf[CheckAgencyController]

  "checkAgencyStatus with the agentAssuranceFlag set to false" should {
    "redirect to confirm agency page and store known facts result in the session store when a matching registration is found for the UTR and postcode" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)

      sessionStoreService.currentSession.knownFactsResult shouldBe Some(KnownFactsResult(validUtr, validPostcode, "My Agency", isSubscribedToAgentServices = false))
    }

    "store isSubscribedToAgentServices = false in session when the business registration found by agent-subscription is not already subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
      sessionStoreService.currentSession.knownFactsResult.get.isSubscribedToAgentServices shouldBe false
    }

    "store isSubscribedToAgentServices = true in session when the business registration found by agent-subscription is already subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = true)
      hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
      sessionStoreService.currentSession.knownFactsResult.get.isSubscribedToAgentServices shouldBe true
    }
  }

  "showCheckAgencyStatus" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showCheckAgencyStatus(request))

    behave like aPageWithFeedbackLinks(request => {
      hasNoEnrolments(subscribingAgent)
      controller.showCheckAgencyStatus(request)
    }, authenticatedRequest())

    "display the check agency status page if the current user is logged in and has affinity group = Agent" in {
      hasNoEnrolments(subscribingAgent)

      val result = await(controller.showCheckAgencyStatus(authenticatedRequest()))

      checkHtmlResultWithBodyText(result, "Identify your business")
    }

    "display the AS Account Page if the current user has HMRC-AS-AGENT enrolment" in {
      isSubscribedToMtd(subscribingAgent)

      val result = await(controller.showCheckAgencyStatus(authenticatedRequest()))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(redirectUrl)
    }

    "redirect to Already Subscribed page if current user has HMRC-AS-AGENT enrolment" in {
      isSubscribedToMtdNotActivated(subscribingAgent)

      val result = await(controller.showCheckAgencyStatus(authenticatedRequest()))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showAlreadySubscribed().url)
    }
  }

  "checkAgencyStatus" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.checkAgencyStatus(request))

    "return a 200 response to redisplay the form with an error message for invalidly-formatted UTR" in {
      val invalidUtr = "0123456"
      hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> invalidUtr, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include("Identify your business")
      responseBody should include("Please enter a valid UTR")
      responseBody should include(invalidUtr)
      responseBody should include(validPostcode)
    }

    "return a 200 response to redisplay the form with an error message for UTR failing to pass Modulus11Check" in {
      val invalidUtr = "2000000001" // Modulus11Check validation fails in this case
      hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> invalidUtr, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include("Identify your business")
      responseBody should include("Please enter a valid UTR")
      responseBody should include(invalidUtr)
      responseBody should include(validPostcode)
    }

    "return a 200 response to redisplay the form with an error message for invalidly-formatted postcode" in {
      hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> invalidPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include("Identify your business")
      responseBody should include("You have entered an invalid postcode")
      responseBody should include(validUtr.value)
      responseBody should include(invalidPostcode)
    }

    "return a 200 response to redisplay the form with an error message for empty form parameters" in {
      hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> "", "postcode" -> "")
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include("Identify your business")
      responseBody should include("This field is required")
    }

    "redirect to no-agency-found page when no matching registration found by agent-subscription" in {
      hasNoEnrolments(subscribingAgent)
      withNonMatchingUtrAndPostcode(validUtr, validPostcode)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showNoAgencyFound().url)
    }

    "propagate an exception when there is no organisation name" in {
      withNoOrganisationName(validUtr, validPostcode)
      hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val e = intercept[IllegalStateException] {
        await(controller.checkAgencyStatus(request))
      }
      e.getMessage should include(validUtr.value)
    }

  }

  "showHasOtherEnrolments" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showHasOtherEnrolments(request))
    behave like aPageWithFeedbackLinks(request => {
      hasNoEnrolments(subscribingAgent)
      controller.showHasOtherEnrolments(request)
    }, authenticatedRequest())

    "display the has other enrolments page if the current user is logged in and has affinity group = Agent" in {
      isEnrolledForNonMtdServices(subscribingAgent)

      val result = await(controller.showHasOtherEnrolments(authenticatedRequest()))

      checkHtmlResultWithBodyText(result, "Create your new Agent Services account")
    }
  }

  "showNoAgencyFound" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showNoAgencyFound(request))
    behave like aPageWithFeedbackLinks(request => {
      hasNoEnrolments(subscribingAgent)
      controller.showNoAgencyFound(request)
    }, authenticatedRequest())

    "display the no agency found page if the current user is logged in and has affinity group = Agent" in {
      hasNoEnrolments(subscribingAgent)

      val result = await(controller.showNoAgencyFound(authenticatedRequest()))

      checkHtmlResultWithBodyText(result, htmlEscapedMessage("noAgencyFound.title"))
    }
  }

  "showConfirmYourAgency" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showConfirmYourAgency(request))

    "display the confirm your agency page if the current user is logged in and has affinity group = Agent" in {
      val utr = Utr("0123456789")
      val postcode = "AA11AA"
      val registrationName = "My Agency"

      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(utr = utr, postcode = postcode, taxpayerName = registrationName, isSubscribedToAgentServices = false))
      hasNoEnrolments(subscribingAgent)

      val result = await(controller.showConfirmYourAgency(request))

      checkHtmlResultWithBodyText(result,
        htmlEscapedMessage("confirmYourAgency.title"),
        s">$postcode</", s">${utr.value}</", s">$registrationName</")
    }

    "show a button which allows the user to return to Check Agency Status page" in {
      hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(utr = Utr("0123456789"), postcode = "AA11AA", taxpayerName = "My Agency", isSubscribedToAgentServices = false))

      val result = await(controller.showConfirmYourAgency(request))

      checkHtmlResultWithBodyText(result, routes.CheckAgencyController.showCheckAgencyStatus().url)
    }

    "show a Continue button which allows the user to go to Subscription Details if isSubscribedToAgentServices=false" in {
      hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(utr = Utr("0123456789"), postcode = "AA11AA", taxpayerName = "My Agency", isSubscribedToAgentServices = false))

      val result = await(controller.showConfirmYourAgency(request))

      checkHtmlResultWithBodyText(result, routes.SubscriptionController.showInitialDetails().url)
    }

    "show a Continue button which allows the user to go to Already Subscribed if isSubscribedToAgentServices=true" in {
      hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(utr = Utr("0123456789"), postcode = "AA11AA", taxpayerName = "My Agency", isSubscribedToAgentServices = true))

      val result = await(controller.showConfirmYourAgency(request))

      checkHtmlResultWithBodyText(result, routes.CheckAgencyController.showAlreadySubscribed().url)
    }

    "redirect to the Check Agency Status page if there is no KnownFactsResult in session because the user has returned to a bookmark" in {
      hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()

      val result = await(controller.showConfirmYourAgency(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "showAlreadySubscribed" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showAlreadySubscribed(request))

    "display the already subscribed page if the current user is logged in and has affinity group = Agent" in {
      hasNoEnrolments(subscribingAgent)

      val result = await(controller.showAlreadySubscribed(authenticatedRequest()))

      checkHtmlResultWithBodyText(result, "Your agency is already subscribed")
    }
  }

}
