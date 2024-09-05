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

import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{Llp, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessType, DateOfBirth}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.agentSession
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestSetupNoJourneyRecord}
import uk.gov.hmrc.domain.Nino

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class NationalInsuranceControllerISpecIt extends BaseISpecIt with SessionDataMissingSpec {

  lazy val controller: NationalInsuranceController = app.injector.instanceOf[NationalInsuranceController]

  "show /national-insurance-number form" should {
    "display the form as expected when nino exists" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(BusinessType.SoleTrader)))(request, global, aesCrypto))

      val result: Result = await(controller.showNationalInsuranceNumberForm()(request))

      result should containMessages(
        "nino.title",
        "nino.hint"
      )

      result should containLink("button.back", routes.PostcodeController.showPostcodeForm().url)
    }

    "display the form as expected when businessType is LLP and nino does not exist" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(BusinessType.Llp)))(request, global, aesCrypto))

      val result: Result = await(controller.showNationalInsuranceNumberForm()(request))

      result should containMessages(
        "nino.title",
        "nino.hint-llp"
      )
      result should containLink("button.back", routes.CompanyRegistrationController.showLlpInterrupt().url)
    }

    "redirect to /cannot-confirm-identity page if nino is marked as deceased in citizen details" in new TestSetupNoJourneyRecord {
      AgentSubscriptionStub.givenDesignatoryDetailsForNino(Nino("AE123456C"), Some("Matchmaker"), DateOfBirth(LocalDate.now()), deceased = true)
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")), POST).withFormUrlEncodedBody("nino" -> "AE123456C")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result: Result = await(controller.submitNationalInsuranceNumberForm()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showCannotConfirmIdentity().url)

    }

    "redirect to /registered-for-vat page when businessType is S.T. or Partnership and nino doesn't exist" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(BusinessType.SoleTrader)))(request, global, aesCrypto))

      val result: Result = await(controller.showNationalInsuranceNumberForm()(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.VatDetailsController.showRegisteredForVatForm().url)
    }

    "redirect to /business-type page when session contains wrong businessType" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(BusinessType.LimitedCompany)))(request, global, aesCrypto))

      val result: Result = await(controller.showNationalInsuranceNumberForm()(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }

    "pre-populate the NINO if one is already stored in the session" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader), Some("abcd"), nino = Some("AE123456C")))(request, global, aesCrypto))

      val result: Result = await(controller.showNationalInsuranceNumberForm()(request))

      result should containInputElement("nino", "text", Some("AE123456C"))
    }
  }

  "submit /national-insurance-number form" should {
    "read the form and redirect to /date-of-birth page if dob exists but lastName doesn't in /citizen-details" in new TestSetupNoJourneyRecord {
      AgentSubscriptionStub.givenDesignatoryDetailsForNino(Nino("AE123456C"), None, DateOfBirth(LocalDate.now()))
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")), POST).withFormUrlEncodedBody("nino" -> "AE123456C")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result: Result = await(controller.submitNationalInsuranceNumberForm()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.DateOfBirthController.showDateOfBirthForm().url)

      sessionStoreService.currentSession.agentSession.flatMap(_.dateOfBirthFromCid) shouldBe Some(DateOfBirth(LocalDate.now()))
      sessionStoreService.currentSession.agentSession.flatMap(_.nino) shouldBe Some("AE123456C")
    }

    "read the form and redirect to /date-of-birth page if both name and dob exists in /citizen-details " +
      "and businessType is LLP" in new TestSetupNoJourneyRecord {
        AgentSubscriptionStub.givenDesignatoryDetailsForNino(Nino("AE123456C"), Some("Matchmaker"), DateOfBirth(LocalDate.now()))
        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] = authenticatedAs(
          subscribingAgentEnrolledForNonMTD
            .copy(nino = Some("AE123456C")),
          POST
        ).withFormUrlEncodedBody("nino" -> "AE123456C")
        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(Llp)))

        val result: Result = await(controller.submitNationalInsuranceNumberForm()(request))

        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.DateOfBirthController.showDateOfBirthForm().url)

        sessionStoreService.currentSession.agentSession.flatMap(_.dateOfBirthFromCid) shouldBe Some(DateOfBirth(LocalDate.now()))
        sessionStoreService.currentSession.agentSession.flatMap(_.lastNameFromCid) shouldBe Some("Matchmaker")
        sessionStoreService.currentSession.agentSession.flatMap(_.nino) shouldBe Some("AE123456C")
      }

    "submit /national-insurance-number form" should {
      "read the form and redirect to /no match page if name or dob is missing in /citizen-details and businessType is LLP" in new TestSetupNoJourneyRecord {
        AgentSubscriptionStub.givenDesignatoryDetailsForNino(Nino("AE123456C"), None, DateOfBirth(LocalDate.now()))
        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] = authenticatedAs(
          subscribingAgentEnrolledForNonMTD
            .copy(nino = Some("AE123456C")),
          POST
        ).withFormUrlEncodedBody("nino" -> "AE123456C")
        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(Llp)))

        val result: Result = await(controller.submitNationalInsuranceNumberForm()(request))

        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showNoMatchFound().url)

        sessionStoreService.currentSession.agentSession.flatMap(_.dateOfBirthFromCid) shouldBe None
        sessionStoreService.currentSession.agentSession.flatMap(_.lastNameFromCid) shouldBe None
        sessionStoreService.currentSession.agentSession.flatMap(_.nino) shouldBe Some("AE123456C")
      }

      "read the form and redirect to /registered-for-vat page if dob doesn't exist in /citizen-details" in new TestSetupNoJourneyRecord {
        AgentSubscriptionStub.givenDesignatoryDetailsReturnsStatus(Nino("AE123456C"), 404)
        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")), POST).withFormUrlEncodedBody("nino" -> "AE123456C")
        sessionStoreService.currentSession.agentSession = Some(agentSession)
        val result: Result = await(controller.submitNationalInsuranceNumberForm()(request))
        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.VatDetailsController.showRegisteredForVatForm().url)

        sessionStoreService.currentSession.agentSession.flatMap(_.dateOfBirthFromCid) shouldBe None
        sessionStoreService.currentSession.agentSession.flatMap(_.nino) shouldBe Some("AE123456C")
      }

      "redirect to /no-match-found page if nino from auth and nino from user input do not match" in new TestSetupNoJourneyRecord {
        AgentSubscriptionStub.givenDesignatoryDetailsForNino(Nino("AE123456C"), Some("Matchmaker"), DateOfBirth(LocalDate.now()))
        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")), POST).withFormUrlEncodedBody("nino" -> "AE123456D")
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        val result: Result = await(controller.submitNationalInsuranceNumberForm()(request))

        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showNoMatchFound().url)

      }

      "redirect to /date-of-birth if nino from auth and nino from user input do not match " +
        "and businessType is LLP (although we do not expect a auth nino for LLP)" in new TestSetupNoJourneyRecord {
          AgentSubscriptionStub.givenDesignatoryDetailsForNino(Nino("AE123456D"), Some("Matchmaker"), DateOfBirth(LocalDate.now()))
          implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
            authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")), POST).withFormUrlEncodedBody("nino" -> "AE123456D")
          sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(Llp)))

          val result: Result = await(controller.submitNationalInsuranceNumberForm()(request))

          status(result) shouldBe 303

          redirectLocation(result) shouldBe Some(routes.DateOfBirthController.showDateOfBirthForm().url)

        }

      "handle forms with invalid nino" in new TestSetupNoJourneyRecord {
        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("nino" -> "AE123456C_BLAH")
        sessionStoreService.currentSession.agentSession = Some(agentSession)
        val result: Result = await(controller.submitNationalInsuranceNumberForm()(request))

        status(result) shouldBe 200

        result should containMessages(
          "nino.title",
          "nino.hint",
          "error.nino.invalid"
        )
      }
    }
  }

}
