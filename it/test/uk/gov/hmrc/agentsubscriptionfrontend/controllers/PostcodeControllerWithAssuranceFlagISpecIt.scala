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
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, Llp, Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, Postcode}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.Css.{errorForField, errorSummaryForField, labelFor}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestSetupNoJourneyRecord}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PostcodeControllerWithAssuranceFlagISpecIt extends BaseISpecIt with SessionDataMissingSpec {

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder
      .configure(
        "features.agent-assurance-run"        -> true,
        "features.agent-assurance-paye-check" -> true,
        "government-gateway.url"              -> configuredGovernmentGatewayUrl
      )

  lazy val controller: PostcodeController = app.injector.instanceOf[PostcodeController]

  "GET /postcode" should {
    "display the postcode page with content tailored to the business type - Sole Trader" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(postcode = None, nino = None))
      val result = await(controller.showPostcodeForm()(request))

      status(result) shouldBe 200

      val html = Jsoup.parse(Helpers.contentAsString(Future.successful(result)))
      html.title() shouldBe "What is the postcode of the address you registered with HMRC for Self Assessment? - Create an agent services account - GOV.UK"
      html.select("label[for=postcode]").text() shouldBe "What is the postcode of the address you registered with HMRC for Self Assessment?"
      html.select("#postcode-hint").text() shouldBe "This could be a home address."
    }

    "display the postcode page with content tailored to the business type - Limited Company" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(LimitedCompany), postcode = None, nino = None))
      val result = await(controller.showPostcodeForm()(request))

      status(result) shouldBe 200

      val html = Jsoup.parse(Helpers.contentAsString(Future.successful(result)))
      html.select("label[for=postcode]").text() shouldBe "What is the postcode of your registered office?"
      html
        .select("#postcode-hint")
        .text() shouldBe "The registered office address is an address you submitted to Companies House when you registered your limited liability partnership (LLP) or limited company."
    }

    "display the postcode page with content tailored to the business type - Limited Liability Partnership" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(Llp), postcode = None, nino = None))
      val result = await(controller.showPostcodeForm()(request))

      status(result) shouldBe 200

      val html = Jsoup.parse(Helpers.contentAsString(Future.successful(result)))
      html.title() shouldBe "What is the postcode of your registered office? - Create an agent services account - GOV.UK"
      html.select("label[for=postcode]").text() shouldBe "What is the postcode of your registered office?"
      html
        .select("#postcode-hint")
        .text() shouldBe "The registered office address is an address you submitted to Companies House when you registered your limited liability partnership (LLP) or limited company."
    }

    "display the postcode page with content tailored to the business type - Partnership" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(Partnership), postcode = None, nino = None))
      val result = await(controller.showPostcodeForm()(request))

      status(result) shouldBe 200

      val html = Jsoup.parse(Helpers.contentAsString(Future.successful(result)))
      html.title() shouldBe "What is the postcode of the address you registered with HMRC for Self Assessment? - " +
        "Create an agent services account - GOV.UK"
      html.select("label[for=postcode]").text() shouldBe "What is the postcode of the address you registered with HMRC for Self Assessment?"
      html.select("#postcode-hint").text() shouldBe "This could be a home address."
    }
  }

  "POST /postcode" when {

    def stubs(isMAA: Boolean = false) = {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      if (isMAA) givenAgentIsManuallyAssured(validUtr.value) else givenAgentIsNotManuallyAssured(validUtr.value)
    }

    "businessType is SoleTrader or Partnership" should {

      "redirect to /national-insurance-number page if nino exists" in new TestSetupNoJourneyRecord {
        List(SoleTrader, Partnership).foreach { businessType =>
          stubs()
          implicit val request =
            authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")), POST)
              .withFormUrlEncodedBody("postcode" -> validPostcode)

          sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(businessType), postcode = None, nino = None))

          val result = await(controller.submitPostcodeForm()(request))

          status(result) shouldBe 303

          redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)

          sessionStoreService.currentSession.agentSession shouldBe
            Some(
              agentSession.copy(
                businessType = Some(businessType),
                postcode = Some(Postcode(validPostcode)),
                nino = None,
                registration = Some(testRegistration.copy(emailAddress = Some("someone@example.com"), safeId = None)),
                isMAA = Some(false)
              )
            )
        }
      }

      "redirect to /registered-for-vat page if nino doesn't exist" in new TestSetupNoJourneyRecord {
        List(SoleTrader, Partnership).foreach { businessType =>
          stubs()
          implicit val request =
            authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("postcode" -> validPostcode)

          sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(businessType), postcode = None, nino = None))

          val result = await(controller.submitPostcodeForm()(request))

          status(result) shouldBe 303

          redirectLocation(result) shouldBe Some(routes.VatDetailsController.showRegisteredForVatForm().url)

          sessionStoreService.currentSession.agentSession shouldBe
            Some(
              agentSession.copy(
                businessType = Some(businessType),
                postcode = Some(Postcode(validPostcode)),
                nino = None,
                registration = Some(testRegistration.copy(emailAddress = Some("someone@example.com"), safeId = None)),
                isMAA = Some(false)
              )
            )
        }
      }
    }

    "businessType is Limited Company" should {

      "redirect to /company-registration-number" in new TestSetupNoJourneyRecord {

        stubs()
        implicit val request =
          authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("postcode" -> validPostcode)
        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(LimitedCompany), postcode = None, nino = None))

        val result = await(controller.submitPostcodeForm()(request))

        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.CompanyRegistrationController.showCompanyRegNumberForm().url)

        sessionStoreService.currentSession.agentSession.get.registration shouldBe Some(
          testRegistration.copy(emailAddress = Some("someone@example.com"), safeId = None)
        )

      }
    }

    "business type is Llp" should {
      "redirect to /confirm-business when the agent is on the manually assured list" in new TestSetupNoJourneyRecord {
        stubs(true)
        implicit val request =
          authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("postcode" -> validPostcode)
        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(Llp), postcode = None, nino = None))

        val result = await(controller.submitPostcodeForm()(request))

        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

        sessionStoreService.currentSession.agentSession.get.registration shouldBe Some(
          testRegistration.copy(emailAddress = Some("someone@example.com"), safeId = None)
        )

      }

      "redirect to /company-registration-number when the agent is not on the manually assured list" in new TestSetupNoJourneyRecord {
        stubs(false)
        implicit val request =
          authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("postcode" -> validPostcode)
        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(Llp), postcode = None, nino = None))

        val result = await(controller.submitPostcodeForm()(request))

        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.CompanyRegistrationController.showCompanyRegNumberForm().url)

        sessionStoreService.currentSession.agentSession.get.registration shouldBe Some(
          testRegistration.copy(emailAddress = Some("someone@example.com"), safeId = None)
        )

      }
    }

    "fail when a matching registration is found for the UTR and postcode for an agent when failing the SaAgent check" in new TestSetupNoJourneyRecord {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
        .withFormUrlEncodedBody("postcode" -> validPostcode)
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AssuranceChecksController.invasiveCheckStart().url)
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = None,
        passSaAgentAssuranceCheck = Some(false),
        passVatDecOrgAgentAssuranceCheck = Some(false),
        passIRCTAgentAssuranceCheck = Some(false)
      )

      await(sessionStoreService.fetchAgentSession).get.registration.get.taxpayerName shouldBe Some(registrationName)
    }

    "redirect to /business-type if businessType is not found in session" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("postcode" -> "AA12 1JN")

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }

    "redirect to /utr if there is no utr in the session" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("postcode" -> "AA12 1JN")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(postcode = None, nino = None, utr = None))

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.UtrController.showUtrForm().url)
    }

    "redirect to cannot create account if user is on the refusal to deal with list" in new TestSetupNoJourneyRecord {
      givenRefusalToDealWithUtrIsForbidden(validUtr.value)
      withMatchingUtrAndPostcode(validUtr, validPostcode)

      implicit val request =
        authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("postcode" -> validPostcode)
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(postcode = None, nino = None))

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)
    }

    "handle for with invalid postcodes" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("postcode" -> "sdsds")
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader))))

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 200

      private val content: String = Helpers.contentAsString(Future.successful(result))
      val html = Jsoup.parse(content)
      html.title() shouldBe "Error: What is the postcode of the address you registered with HMRC for Self Assessment? - Create an agent services account - GOV.UK"
      html.select("#postcode-hint").text() shouldBe "This could be a home address."
      html.select(labelFor("postcode")).text() shouldBe "What is the postcode of the address you registered with HMRC for Self Assessment?"
      html.select(errorForField("postcode")).text() shouldBe "Error: Enter a valid postcode, for example AA1 1AA"
      html.select(errorSummaryForField("postcode")).text() shouldBe "Enter a valid postcode, for example AA1 1AA"
    }
  }
}
