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

import play.api.i18n.Lang
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, Llp, Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, CompletePartialSubscriptionBody, SubscriptionRequestKnownFacts}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestSetupNoJourneyRecord}

import scala.concurrent.ExecutionContext.Implicits.global

class PostcodeControllerWithOutAssuranceFlagISpecIt extends BaseISpecIt with SessionDataMissingSpec {

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder
      .configure(
        "features.agent-assurance-run"        -> false,
        "features.agent-assurance-paye-check" -> true,
        "government-gateway.url"              -> configuredGovernmentGatewayUrl
      )

  lazy val controller: PostcodeController = app.injector.instanceOf[PostcodeController]

  "POST /postcode" when {

    "businessType is SoleTrader or Partnership" should {

      "redirect to /national-insurance-number page if nino exists" in new TestSetupNoJourneyRecord {
        List(SoleTrader, Partnership).foreach { businessType =>
          withMatchingUtrAndPostcode(validUtr, validPostcode)

          implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
            authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")), POST)
              .withFormUrlEncodedBody("postcode" -> validPostcode)
          sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(businessType), postcode = None, nino = None))

          val result = await(controller.submitPostcodeForm()(request))

          status(result) shouldBe 303

          redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)

          sessionStoreService.currentSession.agentSession.get.registration shouldBe Some(
            testRegistration.copy(emailAddress = Some("someone@example.com"), safeId = None)
          )
        }
      }

      "redirect to /registered-for-vat page if nino doesn't exist" in new TestSetupNoJourneyRecord {
        List(SoleTrader, Partnership).foreach { businessType =>
          withMatchingUtrAndPostcode(validUtr, validPostcode)

          implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
            authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("postcode" -> validPostcode)
          sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(businessType), postcode = None, nino = None))

          val result = await(controller.submitPostcodeForm()(request))

          status(result) shouldBe 303

          redirectLocation(result) shouldBe Some(routes.VatDetailsController.showRegisteredForVatForm().url)

          sessionStoreService.currentSession.agentSession.get.registration shouldBe Some(
            testRegistration.copy(emailAddress = Some("someone@example.com"), safeId = None)
          )
        }
      }
    }

    "businessType is Limited Company or Llp" should {
      "redirect to /company-registration-number" in new TestSetupNoJourneyRecord {
        List(LimitedCompany, Llp).foreach { businessType =>
          withMatchingUtrAndPostcode(validUtr, validPostcode)

          implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
            authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("postcode" -> validPostcode)
          sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(businessType), postcode = None, nino = None))

          val result = await(controller.submitPostcodeForm()(request))

          status(result) shouldBe 303

          redirectLocation(result) shouldBe Some(routes.CompanyRegistrationController.showCompanyRegNumberForm().url)

          sessionStoreService.currentSession.agentSession.get.registration shouldBe Some(
            testRegistration.copy(emailAddress = Some("someone@example.com"), safeId = None)
          )
        }
      }

    }

    "redirect to already subscribed page when the business registration found by agent-subscription is already subscribed" in new TestSetupNoJourneyRecord {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = true, isSubscribedToETMP = true)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
        .withFormUrlEncodedBody("postcode" -> validPostcode)
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(SoleTrader), postcode = None, nino = None))

      val result: Result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showAlreadySubscribed().url)
    }

    "showSubscriptionComplete for partially subscribed agent" in new TestSetupNoJourneyRecord {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = false, isSubscribedToETMP = true)
      partialSubscriptionWillSucceed(
        CompletePartialSubscriptionBody(validUtr, knownFacts = SubscriptionRequestKnownFacts(validPostcode), langForEmail = Some(Lang("en"))),
        arn = "TARN00023"
      )

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments.copy(nino = Some("AE123456C")), POST)
          .withFormUrlEncodedBody("postcode" -> validPostcode)
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result: Result = await(controller.submitPostcodeForm()(request))
      redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)
    }

    "redirect to no match found when the subscription status is strange" in new TestSetupNoJourneyRecord {
      withNonMatchingUtrAndPostcode(validUtr, validPostcode)
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] = authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
        .withFormUrlEncodedBody("postcode" -> validPostcode)
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result: Result = await(controller.submitPostcodeForm()(request))
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showNoMatchFound().url)
    }

    "redirect to /business-type if businessType is not found in session" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("postcode" -> "AA12 1JN")

      val result: Result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }

    "handle for with invalid postcodes" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("postcode" -> "sdsds")
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader)))(request, global, aesCrypto))

      val result: Result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 200

      result should containMessages(
        "postcode.sole_trader.title",
        "error.postcode.invalid"
      )
    }
  }
}
