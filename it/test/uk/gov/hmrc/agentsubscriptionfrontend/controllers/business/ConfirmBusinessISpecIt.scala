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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers.business

import play.api.i18n.Lang
import play.api.mvc.{AnyContentAsFormUrlEncoded, Cookie}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.{BusinessIdentificationController, routes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{subscribingAgentEnrolledForNonMTD, subscribingCleanAgentWithoutEnrolments}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{businessAddress, testPostcode, utr, _}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestSetupNoJourneyRecord}
import uk.gov.hmrc.http.BadRequestException

class ConfirmBusinessISpecIt extends BaseISpecIt {
  lazy val controller: BusinessIdentificationController = app.injector.instanceOf[BusinessIdentificationController]

  "showConfirmBusinessForm" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showConfirmBusinessForm(request))

    "display the confirm business page if the current user is logged in and has affinity group = Agent" in new TestSetupNoJourneyRecord {
      val utr = Utr("0123456789")
      val postcode = "AA11AA"
      val registrationName = "My Agency"

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(utr), registration = Some(testRegistration)))

      val result = await(controller.showConfirmBusinessForm(request))

      result should containMessages("confirmBusiness.title", "button.back", "confirmBusiness.option.yes", "confirmBusiness.option.no")

      result should containSubstrings(
        s"$postcode",
        "01234 56789",
        s"$registrationName",
        s"${businessAddress.addressLine1}",
        s"${businessAddress.addressLine2.get}",
        s"${businessAddress.addressLine3.get}",
        s"${businessAddress.addressLine4.get}"
      )
    }

    "show utr in the correct format" in new TestSetupNoJourneyRecord {
      val utr = Utr("0123456789")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(utr), registration = Some(testRegistration)))

      val result = await(controller.showConfirmBusinessForm(request))

      result should containSubstrings("01234 56789")
    }

    "redirect to GET /business-type when no businessType in session" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      redirectLocation(await(controller.showConfirmBusinessForm(request))).get shouldBe routes.BusinessTypeController
        .showBusinessTypeForm()
        .url
    }

    "show a back button correct when they are NOT registered for vat" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingAgentEnrolledForNonMTD)

      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(utr), registeredForVat = Some("No"), registration = Some(testRegistration)))

      val result = await(controller.showConfirmBusinessForm(request))

      result should containSubstrings(routes.VatDetailsController.showRegisteredForVatForm().url)
    }

    "show a back button correct when they are registered for vat and provided vat details" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingAgentEnrolledForNonMTD)

      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(utr), registeredForVat = Some("Yes"), registration = Some(testRegistration)))

      val result = await(controller.showConfirmBusinessForm(request))

      result should containSubstrings(routes.VatDetailsController.showVatDetailsForm().url)
    }
  }

  "submitConfirmBusiness" when {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitConfirmBusinessForm(request))

    "User chooses Yes" should {
      "redirect to showAlreadySubscribed if the user is already subscribed and isSubscribedToAgentServices=true" in new TestSetupNoJourneyRecord {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
          .withFormUrlEncodedBody("confirmBusiness" -> "yes")
        sessionStoreService.currentSession.agentSession = Some(
          AgentSession(Some(BusinessType.SoleTrader), utr = Some(utr), registration = Some(testRegistration.copy(isSubscribedToAgentServices = true)))
        )

        val result = await(controller.submitConfirmBusinessForm(request))

        result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController.showAlreadySubscribed().url
        metricShouldExistAndBeUpdated("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
      }

      "redirect to task list if the user has clean creds and isSubscribedToAgentServices=false" in new TestSetupNoJourneyRecord {
        val agentSession = AgentSession(
          Some(BusinessType.SoleTrader),
          utr = Some(utr),
          registration = Some(testRegistration.copy(isSubscribedToAgentServices = false)),
          postcode = Some(Postcode("AA1 1AA"))
        )

        val sjr = SubscriptionJourneyRecord.fromAgentSession(agentSession, AuthProviderId("12345-credId"))

        givenSubscriptionRecordCreated(AuthProviderId("12345-credId"), sjr.copy(continueId = None))

        givenAgentIsNotManuallyAssured(utr.value)
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
          .withFormUrlEncodedBody("confirmBusiness" -> "yes")
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        val result = await(controller.submitConfirmBusinessForm(request))

        result.header.headers(LOCATION) shouldBe routes.TaskListController.showTaskList().url
      }

      "redirect to task list if user has clean creds " +
        "and isSubscribedToAgentServices=false and is a MAA" in new TestSetupNoJourneyRecord {
          val agentSession = AgentSession(
            Some(BusinessType.SoleTrader),
            utr = Some(utr),
            registration = Some(testRegistration.copy(isSubscribedToAgentServices = false)),
            postcode = Some(Postcode("AA11AA"))
          )

          val sjr = SubscriptionJourneyRecord.fromAgentSession(agentSession, AuthProviderId("12345-credId"))
          givenSubscriptionRecordCreated(AuthProviderId("12345-credId"), sjr.copy(continueId = None))
          givenAgentIsManuallyAssured(utr.value)
          implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
            .withFormUrlEncodedBody("confirmBusiness" -> "yes")
          sessionStoreService.currentSession.agentSession = Some(agentSession)

          val result = await(controller.submitConfirmBusinessForm(request))

          result.header.headers(LOCATION) shouldBe routes.TaskListController.showTaskList().url
        }
      "redirect to subscription complete if user is partially subscribed with clean creds" in new TestSetupNoJourneyRecord {
        givenAgentIsNotManuallyAssured(utr.value)
        withPartiallySubscribedAgent(utr, testPostcode)
        partialSubscriptionWillSucceed(
          CompletePartialSubscriptionBody(utr = utr, knownFacts = SubscriptionRequestKnownFacts(testPostcode), langForEmail = Some(Lang("en")))
        )

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
          .withFormUrlEncodedBody("confirmBusiness" -> "yes")
          .withCookies(Cookie("PLAY_LANG", "en"))
        sessionStoreService.currentSession.agentSession = Some(
          AgentSession(
            Some(BusinessType.SoleTrader),
            utr = Some(utr),
            registration = Some(testRegistration.copy(isSubscribedToETMP = true)),
            postcode = Some(Postcode(testPostcode))
          )
        )

        val result = await(controller.submitConfirmBusinessForm(request))

        result.header.headers(LOCATION) shouldBe routes.SubscriptionController.showSubscriptionComplete().url

      }

      "redirect to showCannotCreateAccount if the subscribing agent has been terminated" in new TestSetupNoJourneyRecord {
        givenAgentIsNotManuallyAssured(utr.value)
        withPartiallySubscribedAgent(
          utr,
          testPostcode
        ) // this is a temporary case where terminated agents seem to only be partially terminated in ETMP
        partialSubscriptionWillFailAgentTerminated(
          CompletePartialSubscriptionBody(utr = utr, knownFacts = SubscriptionRequestKnownFacts(testPostcode), langForEmail = Some(Lang("en")))
        )
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST)
          .withFormUrlEncodedBody("confirmBusiness" -> "yes")
          .withCookies(Cookie("PLAY_LANG", "en"))
        sessionStoreService.currentSession.agentSession = Some(
          AgentSession(
            Some(BusinessType.SoleTrader),
            utr = Some(utr),
            registration = Some(testRegistration.copy(isSubscribedToETMP = true)),
            postcode = Some(Postcode(testPostcode))
          )
        )

        val result = await(controller.submitConfirmBusinessForm(request))

        result.header.headers(LOCATION) shouldBe routes.StartController.showCannotCreateAccount().url
      }

      "redirect to sign in with new user ID if the user is partially subscribed with unclean creds" in new TestSetupNoJourneyRecord {
        val agentSession = AgentSession(
          Some(BusinessType.SoleTrader),
          utr = Some(utr),
          registration = Some(testRegistration.copy(isSubscribedToETMP = true)),
          postcode = Some(Postcode(testPostcode))
        )
        givenAgentIsNotManuallyAssured(utr.value)
        withPartiallySubscribedAgent(utr, testPostcode)
        val sjr = SubscriptionJourneyRecord.fromAgentSession(agentSession, AuthProviderId("12345-credId"))
        givenSubscriptionRecordCreated(AuthProviderId("12345-credId"), sjr.copy(continueId = None))

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
          .withFormUrlEncodedBody("confirmBusiness" -> "yes")
          .withCookies(Cookie("PLAY_LANG", "en"))
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        val result = await(controller.submitConfirmBusinessForm(request))

        result.header.headers(LOCATION) shouldBe routes.SubscriptionController.showSignInWithNewID().url
      }

      "redirect to showBusinessEmailForm if the user has clean creds and isSubscribedToAgentServices=false and ETMP " +
        "record contains empty email" in new TestSetupNoJourneyRecord {
          implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
            .withFormUrlEncodedBody("confirmBusiness" -> "yes")
          sessionStoreService.currentSession.agentSession = Some(
            AgentSession(
              Some(BusinessType.SoleTrader),
              utr = Some(utr),
              registration = Some(testRegistration.copy(isSubscribedToAgentServices = false, emailAddress = None))
            )
          )

          val result = await(controller.submitConfirmBusinessForm(request))

          result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController.showBusinessEmailForm().url
        }

      "redirect to showBusinessNameForm if the user has clean creds and isSubscribedToAgentServices=false and ETMP " +
        "record contains invalid name" in new TestSetupNoJourneyRecord {
          implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
            .withFormUrlEncodedBody("confirmBusiness" -> "yes")
          sessionStoreService.currentSession.agentSession = Some(
            AgentSession(
              Some(BusinessType.SoleTrader),
              registration = Some(testRegistration.copy(isSubscribedToAgentServices = false, taxpayerName = None))
            )
          )

          val result = await(controller.submitConfirmBusinessForm(request))

          result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController.showBusinessNameForm().url
        }

      "redirect to showUpdateBusinessAddressForm if the user has clean creds and isSubscribedToAgentServices=false " +
        "and ETMP record contains invalid address" in new TestSetupNoJourneyRecord {
          implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
            .withFormUrlEncodedBody("confirmBusiness" -> "yes")
          sessionStoreService.currentSession.agentSession = Some(
            AgentSession(
              Some(BusinessType.SoleTrader),
              registration =
                Some(testRegistration.copy(isSubscribedToAgentServices = false, address = businessAddress.copy(addressLine1 = "invalid address *")))
            )
          )

          val result = await(controller.submitConfirmBusinessForm(request))

          result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController
            .showUpdateBusinessAddressForm()
            .url
        }

      "redirect to showUpdateBusinessAddressForm if the user has clean creds and isSubscribedToAgentServices=false and" when {
        "ETMP record contains denylisted postcode" in new TestSetupNoJourneyRecord {
          implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
            .withFormUrlEncodedBody("confirmBusiness" -> "yes")
          sessionStoreService.currentSession.agentSession = Some(
            AgentSession(
              Some(BusinessType.SoleTrader),
              registration = Some(
                testRegistration.copy(isSubscribedToAgentServices = false, address = businessAddress.copy(postalCode = Some(denylistedPostcode)))
              )
            )
          )

          val result = await(controller.submitConfirmBusinessForm(request))

          result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController
            .showUpdateBusinessAddressForm()
            .url
        }

        "ETMP record contains BFPO postcode starting with BF" in new TestSetupNoJourneyRecord {
          implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
            .withFormUrlEncodedBody("confirmBusiness" -> "yes")
          sessionStoreService.currentSession.agentSession = Some(
            AgentSession(
              Some(BusinessType.SoleTrader),
              registration =
                Some(testRegistration.copy(isSubscribedToAgentServices = false, address = businessAddress.copy(postalCode = Some("BF1 1XX"))))
            )
          )

          val result = await(controller.submitConfirmBusinessForm(request))

          result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController
            .showUpdateBusinessAddressForm()
            .url
        }

        "ETMP record contains BFPO postcode starting with BFPO" in new TestSetupNoJourneyRecord {
          implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
            .withFormUrlEncodedBody("confirmBusiness" -> "yes")
          sessionStoreService.currentSession.agentSession = Some(
            AgentSession(
              Some(BusinessType.SoleTrader),
              registration = Some(
                testRegistration
                  .copy(isSubscribedToAgentServices = false, address = businessAddress.copy(postalCode = Some("BFPO15")))
              )
            )
          )

          val result = await(controller.submitConfirmBusinessForm(request))

          result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController
            .showUpdateBusinessAddressForm()
            .url
        }
      }
    }

    "User chooses No" should {
      "redirect to show /unique-taxpayer-reference page" in new TestSetupNoJourneyRecord {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
          .withFormUrlEncodedBody("confirmBusiness" -> "no")

        sessionStoreService.currentSession.agentSession = Some(
          AgentSession(
            Some(BusinessType.SoleTrader),
            registration = Some(
              testRegistration
                .copy(isSubscribedToAgentServices = false, address = businessAddress.copy(postalCode = Some("BFPO15")))
            )
          )
        )

        val result = await(controller.submitConfirmBusinessForm(request))

        result.header.headers(LOCATION) shouldBe routes.UtrController.showUtrForm().url
      }
    }

    "choice is missing" should {
      "return 200 and redisplay the /confirm-business page with an error message for missing choice" in new TestSetupNoJourneyRecord {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
          .withFormUrlEncodedBody("confirmBusiness" -> "")
        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(registration = Some(testRegistration)))

        val result = await(controller.submitConfirmBusinessForm(request))

        result should containMessages("confirmBusiness.title", "error.confirm-business-value.invalid")
      }
    }

    "form value is invalid" should {
      "result in a BadRequestException" in new TestSetupNoJourneyRecord {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
          .withFormUrlEncodedBody("confirmBusiness" -> "INVALID")
        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(registration = Some(testRegistration)))

        a[BadRequestException] shouldBe thrownBy(await(controller.submitConfirmBusinessForm(request)))
      }
    }
  }

}
