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

import org.scalatest.Assertion
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessType}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingCleanAgentWithoutEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestSetupNoJourneyRecord}
import uk.gov.hmrc.domain.Nino

class AssuranceChecksControllerISpecIt extends BaseISpecIt {

  lazy val controller: AssuranceChecksController = app.injector.instanceOf[AssuranceChecksController]

  private lazy val messagesApi = app.injector.instanceOf[MessagesApi]
  private implicit lazy val messages: Messages = messagesApi.preferred(Seq.empty[Lang])

  "GET /enter-agent-code" should {

    "display the invasive check start page" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession = Some(agentSession)
      private val result = await(controller.invasiveCheckStart(request))
      status(result) shouldBe 200
      checkHtmlResultWithBodyText(
        result,
        "Do you have a Self Assessment agent code?",
        "We need this information so that we can check your identity.",
        "Your Self Assessment agent code will be on the letter you got from HMRC when you registered " +
          "as an agent for Self Assessment. It’s a 6-character code. For example, SA1234."
      )
    }
  }

  "POST /enter-agent-code" should {

    "return 200 and redisplay the invasiveSaAgentCodePost page with an error message for missing radio choice" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsEmpty.type] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession = Some(agentSession)
      private val result = await(controller.invasiveSaAgentCodePost(request))
      Messages("invasive.error.no-radio.selected").r.findAllMatchIn(bodyOf(result)).size shouldBe 2
    }

    "start invasiveCheck if selected Yes with SaAgentCode" when {

      "input contains only capital letters" in new TestSetupNoJourneyRecord { testSaAgentCodeCheck("SA6012") }
      "input contains letters in mixed case" in new TestSetupNoJourneyRecord { testSaAgentCodeCheck("sA6012") }
      "input contains letters in lower case" in new TestSetupNoJourneyRecord { testSaAgentCodeCheck("sa6012") }

      def testSaAgentCodeCheck(sageAgentCode: String): Assertion = {

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", sageAgentCode))
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        val result = await(controller.invasiveSaAgentCodePost(request))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.AssuranceChecksController.showClientDetailsForm().url)
        noMetricExpectedAtThisPoint()
      }
    }

    "redirect to /cannot-create account if selected No" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
          .withFormUrlEncodedBody(("hasSaAgentCode", "false"))
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      private val result = await(controller.invasiveSaAgentCodePost(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Declined")
    }

    "return 200 and display page with error when failing the validation of SaAgentCode" when {

      "it contains invalid characters" in new TestSetupNoJourneyRecord {

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA601*2AAAA"))
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        private val result = await(controller.invasiveSaAgentCodePost(request))

        result should containMessages("error.saAgentCode.invalid")
        result should repeatMessage("error.saAgentCode.invalid", 2)
        noMetricExpectedAtThisPoint()
      }

      "it contains wrong max length" in new TestSetupNoJourneyRecord {

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA6012AAAA"))
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        private val result = await(controller.invasiveSaAgentCodePost(request))

        result should containMessages("error.saAgentCode.length")
        result should repeatMessage("error.saAgentCode.length", 2)
        noMetricExpectedAtThisPoint()
      }

      "it contains wrong min length" in new TestSetupNoJourneyRecord {

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA"))
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        private val result = await(controller.invasiveSaAgentCodePost(request))

        result should containMessages("error.saAgentCode.length")
        result should repeatMessage("error.saAgentCode.length", 2)
        noMetricExpectedAtThisPoint()
      }

      "contains empty SaAgentCode" in new TestSetupNoJourneyRecord {

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", ""))
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        private val result = await(controller.invasiveSaAgentCodePost(request))

        result should containMessages("error.saAgentCode.blank")
        result should repeatMessage("error.saAgentCode.blank", 2)
        noMetricExpectedAtThisPoint()
      }
    }
  }

  "GET /client-details" should {

    "display the client details page" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsEmpty.type] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      private val result = await(controller.showClientDetailsForm(request))

      status(result) shouldBe 200
      checkHtmlResultWithBodyText(result, "Client details", "We need to identify one of your current clients, so we can confirm who you are.")
    }
  }

  "POST /client-details form" should {

    "redirect to confirm business when successfully submitting nino" when {

      "input nino contains only capital letters" in new TestSetupNoJourneyRecord {
        testInvasiveCheckWithNino("AA123456A")
      }

      "input nino contains mixed case letters" in new TestSetupNoJourneyRecord {
        testInvasiveCheckWithNino("Aa123456a")
      }

      "input nino contains only lowercase letters" in new TestSetupNoJourneyRecord {
        testInvasiveCheckWithNino("aa123456a")
      }

      "input nino contains random spaces" in new TestSetupNoJourneyRecord {
        testInvasiveCheckWithNino("AA1   2 3 4 5 6        A ")
      }

      def testInvasiveCheckWithNino(nino: String): Unit = {

        givenNinoAGoodCombinationAndUserHasRelationshipInCesa(ninoOrUtr = "nino", valueOfNinoOrUtr = "AA123456A", saAgentReference = "SA6012")

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
            .withFormUrlEncodedBody(("variant", "nino"), ("nino", nino))
            .withSession("saAgentReferenceToCheck" -> "SA6012")

        sessionStoreService.currentSession.agentSession = Some(
          AgentSession(
            Some(BusinessType.SoleTrader),
            utr = Some(validUtr),
            postcode = Some(testPostcode),
            registration = Some(testRegistration)
          )
        )

        val result = await(controller.submitClientDetailsForm(request))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(
          routes.NationalInsuranceController.showNationalInsuranceNumberForm().url
        )

        verifyAgentAssuranceAuditRequestSentWithClientIdentifier(
          Nino("AA123456A"),
          passCESAAgentAssuranceCheck = true,
          "SA6012",
          aAssurancePayeCheck = true
        )

        metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
      }
    }

    "redirect to invasive check start when no SACode in session to obtain it again" in new TestSetupNoJourneyRecord {

      givenNinoAGoodCombinationAndUserHasRelationshipInCesa(ninoOrUtr = "nino", valueOfNinoOrUtr = "AA123456A", saAgentReference = "SA6012")

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
          .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123456A"))
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration.copy(emailAddress = None))))

      private val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AssuranceChecksController.invasiveCheckStart().url)
    }

    "redirect to /cannot-create account page when submitting valid nino with no relationship" in new TestSetupNoJourneyRecord {

      givenAUserDoesNotHaveRelationshipInCesa(ninoOrUtr = "nino", valueOfNinoOrUtr = "AA123456A", saAgentReference = "SA6012")

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
          .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123456A"))
          .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession = Some(
        AgentSession(
          Some(BusinessType.SoleTrader),
          utr = Some(validUtr),
          postcode = Some(testPostcode),
          registration = Some(testRegistration)
        )
      )

      private val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(
        Nino("AA123456A"),
        passCESAAgentAssuranceCheck = false,
        "SA6012",
        aAssurancePayeCheck = true
      )

      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Failed")
    }

    "clientDetails no variant selected" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
          .withFormUrlEncodedBody(("variant", ""))

      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result: Result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("clientDetails.error.no-radio.selected"))
    }

    "nino invalid send back 200 with error page" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
          .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123"))

      sessionStoreService.currentSession.agentSession = Some(agentSession)

      private val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.nino.invalid"))
    }

    "nino empty send back 200 with error page" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
          .withFormUrlEncodedBody(("variant", "nino"), ("nino", ""))

      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result: Result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.nino.empty"))
    }

    "redirect to confirm business when successfully submitting UTR" in new TestSetupNoJourneyRecord {

      givenUtrAGoodCombinationAndUserHasRelationshipInCesa(ninoOrUtr = "utr", valueOfNinoOrUtr = "4000000009", saAgentReference = "SA6012")

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] = authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
        .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4000000009"))
        .withSession("saAgentReferenceToCheck" -> "SA6012")

      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(utr = Some(validUtr), postcode = Some(validPostcode)))

      private val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(
        Utr("4000000009"),
        passCESAAgentAssuranceCheck = true,
        "SA6012",
        aAssurancePayeCheck = true
      )

      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
    }

    "redirect to confirm business when successfully submitting UTR with random spaces" in new TestSetupNoJourneyRecord {

      givenUtrAGoodCombinationAndUserHasRelationshipInCesa(ninoOrUtr = "utr", valueOfNinoOrUtr = "4000000009", saAgentReference = "SA6012")

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
          .withFormUrlEncodedBody(("variant", "utr"), ("utr", "   40000      00     009  "))
          .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession = Some(
        agentSession.copy(utr = Some(validUtr), postcode = Some(validPostcode))
      )

      private val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(
        Utr("4000000009"),
        passCESAAgentAssuranceCheck = true,
        "SA6012",
        aAssurancePayeCheck = true
      )

      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
    }

    "redirect to /cannot-create account page" when {

      "submitting valid utr with no relationship" in new TestSetupNoJourneyRecord {

        givenAUserDoesNotHaveRelationshipInCesa(ninoOrUtr = "utr", valueOfNinoOrUtr = "40000     00  009", saAgentReference = "SA6012")

        implicit val request: FakeRequest[AnyContentAsEmpty.type] =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
        sessionStoreService.currentSession.agentSession = Some(
          AgentSession(
            Some(BusinessType.SoleTrader),
            utr = Some(validUtr),
            postcode = Some(testPostcode),
            registration = Some(testRegistration)
          )
        )

        private val result = await(
          controller.submitClientDetailsForm(
            request
              .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4000000009"))
              .withSession("saAgentReferenceToCheck" -> "SA6012")
          )
        )

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

        verifyAgentAssuranceAuditRequestSentWithClientIdentifier(
          Utr("4000000009"),
          passCESAAgentAssuranceCheck = false,
          "SA6012",
          aAssurancePayeCheck = true
        )

        metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Failed")
      }

      "successfully selecting ICannotProvideEitherOfTheseDetails" in new TestSetupNoJourneyRecord {

        givenUtrAGoodCombinationAndUserHasRelationshipInCesa(ninoOrUtr = "utr", valueOfNinoOrUtr = "4000000009", saAgentReference = "SA6012")

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
            .withFormUrlEncodedBody(("variant", "cannotProvide"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        private val result = await(controller.submitClientDetailsForm(request))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

        metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Could-Not-Provide-Tax-Payer-Identifier")
      }
    }

    "utr blank invasiveCheck" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
          .withFormUrlEncodedBody(("variant", "utr"), ("utr", ""))
          .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      private val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.blank"))
    }

    "utr incorrect format send back 200 with error page" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
          .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4ABC000009"))
          .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      private val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.incorrectFormat"))
    }

    "utr invalid send back 200 with error page" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
          .withFormUrlEncodedBody(("variant", "utr"), ("utr", "1234567890"))
          .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      private val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.invalid"))
    }

    "utr wrong length which is less than 10 numbers" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] = authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
        .withFormUrlEncodedBody(("variant", "utr"), ("utr", "40009"))
        .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      private val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.incorrectFormat"))
    }

    "utr wrong length which is more than 10 numbers" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] = authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
        .withFormUrlEncodedBody(("variant", "utr"), ("utr", "40000000090000000"))
        .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      private val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.incorrectFormat"))
    }

    "return 200 error when submitting without selected radio option" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
          .withFormUrlEncodedBody()
          .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      private val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
    }
  }

}
