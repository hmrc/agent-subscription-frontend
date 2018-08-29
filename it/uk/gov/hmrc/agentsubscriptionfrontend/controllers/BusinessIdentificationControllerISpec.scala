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

import org.jsoup.Jsoup
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AgentSubscriptionFrontendEvent
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessAddress, CompletePartialSubscriptionBody, KnownFactsResult, SubscriptionRequestKnownFacts}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub.userIsAuthenticated
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.http.{BadRequestException, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.HeaderCarrierConverter

trait BusinessIdentificationControllerISpec extends BaseISpec with SessionDataMissingSpec {
  val validUtr = Utr("2000000000")
  val validPostcode = "AA1 1AA"
  private val invalidPostcode = "not a postcode"

  val utr = Utr("0123456789")
  val postcode = "AA11AA"
  val registrationName = "My Agency"
  val businessAddress =
    BusinessAddress("AddressLine1 A", Some("AddressLine2 A"), Some("AddressLine3 A"), Some("AddressLine4 A"), Some("AA11AA"), "GB")

  def agentAssuranceRun: Boolean

  def agentAssurancePayeCheck: Boolean

  private lazy val redirectUrl: String = "http://localhost:9401/agent-services-account"

  private lazy val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder
      .configure("features.agent-assurance-run" -> agentAssuranceRun,
        "features.agent-assurance-paye-check" -> agentAssurancePayeCheck,
        "government-gateway.url" -> configuredGovernmentGatewayUrl)

  lazy val controller: BusinessIdentificationController = app.injector.instanceOf[BusinessIdentificationController]

  "showBusinessTypeForm (GET /business-type)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showBusinessTypeForm(_))

    behave like aPageTakingContinueUrlAndCachingInSessionStore(controller.showBusinessTypeForm(_),
      sessionStoreService, userIsAuthenticated(subscribingCleanAgentWithoutEnrolments))

    "contain page titles and header content" in {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))

      result should containMessages("checkBusinessType.title",
        "checkBusinessType.progressive.title",
        "checkBusinessType.progressive.content.p1"
      )
    }

    "contain radio options for Sole Trader, Limited Company, Partnership, and LLP" in {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))
      val doc = Jsoup.parse(bodyOf(result))

      // Check form's radio inputs have correct values
      doc.getElementById("businessType-sole_trader").`val`() shouldBe "sole_trader"
      doc.getElementById("businessType-limited_company").`val`() shouldBe "limited_company"
      doc.getElementById("businessType-partnership").`val`() shouldBe "partnership"
      doc.getElementById("businessType-llp").`val`() shouldBe "llp"
    }

    "contain a link to sign out" in {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))
      val doc = Jsoup.parse(bodyOf(result))
      val signOutLink = doc.getElementById("sign-out")
      signOutLink.attr("href") shouldBe routes.SignedOutController.signOutWithContinueUrl.url
      signOutLink.text() shouldBe htmlEscapedMessage("checkBusinessType.progressive.content.link")
    }
  }

  "submitBusinessTypeForm (POST /business-type)" when {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitBusinessTypeForm(_))

    BusinessIdentificationController.validBusinessTypes.foreach { validBusinessTypeIdentifier =>
      s"redirect to /business-details when valid businessTypeIdentifier: $validBusinessTypeIdentifier" in {
        val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withFormUrlEncodedBody("businessType" -> validBusinessTypeIdentifier)

        val result = await(controller.submitBusinessTypeForm(request))
        result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController.submitBusinessDetailsForm(Some(validBusinessTypeIdentifier)).url
      }
    }

    "choice is missing" should {
      "return 200 and redisplay the /check-business-type page with an error message for missing choice" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        val result = await(controller.submitBusinessTypeForm(request))
        result should containMessages("error.no-radio-selected")
      }
    }

    s"400 Exception ,when businessTypeIdentifier invalid" in {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("businessType" -> "unCateredBusinessTypeIdentifier")

      an[BadRequestException] shouldBe thrownBy(await(controller.submitBusinessTypeForm(request)))
    }
  }

  "showBusinessDetailsForm" should {
    val playRequestValidBusinessTypeIdentifier =
      controller.showBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))

    behave like anAgentAffinityGroupOnlyEndpoint(playRequestValidBusinessTypeIdentifier(_))

    behave like aPageWithFeedbackLinks(playRequestValidBusinessTypeIdentifier(_),
      authenticatedAs(subscribingCleanAgentWithoutEnrolments))

    "showBusinessDetailsForm cache continue url WITH NO business Identifier Input" should {
      //withMaybeContinueUrlCached because, Currently still needed as a user might be arriving from: trusts registration flow or gov.uk guidence page, make sure this is not the case anymore before removing
      behave like aPageTakingContinueUrlAndCachingInSessionStore(controller.showBusinessDetailsForm(None)(_),
        sessionStoreService, userIsAuthenticated(subscribingCleanAgentWithoutEnrolments), expectedStatusCode = 303)
    }

    "showBusinessDetailsForm cache continue url WITH INVALID business Identifier Input" should {
      //withMaybeContinueUrlCached because, Currently still needed as a user might be arriving from: trusts registration flow or gov.uk guidence page, make sure this is not the case anymore before removing
      behave like aPageTakingContinueUrlAndCachingInSessionStore(controller.showBusinessDetailsForm(Some("invalidBusinessTypeIdentifier"))(_),
        sessionStoreService, userIsAuthenticated(subscribingCleanAgentWithoutEnrolments), expectedStatusCode = 303)
    }

    "showBusinessDetailsForm cache continue url WITH valid business type identifier Input" should {
      //withMaybeContinueUrlCached because, Currently still needed as a user might be arriving from: trusts registration flow or gov.uk guidence page, make sure this is not the case anymore before removing
      behave like aPageTakingContinueUrlAndCachingInSessionStore(playRequestValidBusinessTypeIdentifier(_),
        sessionStoreService, userIsAuthenticated(subscribingCleanAgentWithoutEnrolments))
    }

    "display the check agency status page if the current user is logged in and has affinity group = Agent" in {
      val result = await(playRequestValidBusinessTypeIdentifier(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      result should containMessages("businessDetails.title")
      metricShouldExistAndBeUpdated("Count-Subscription-BusinessDetails-Start")
    }

    "display the AS Account Page if the current user has HMRC-AS-AGENT enrolment" in {
      val result = await(playRequestValidBusinessTypeIdentifier(authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(redirectUrl)
      metricShouldExistAndBeUpdated("Count-Subscription-AlreadySubscribed-HasEnrolment-AgentServicesAccount")
    }

    BusinessIdentificationController.validBusinessTypes.foreach { validBusinessTypeIdentifier =>

      s"show check Agency Status page for valid businessTypeIdentifier: $validBusinessTypeIdentifier" in {
        val result = await(controller.showBusinessDetailsForm(Some(validBusinessTypeIdentifier))(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))
        status(result) shouldBe 200
        val bodyString = bodyOf(result)
        bodyString should include(htmlEscapedMessage("businessDetails.title"))
        bodyString should include(htmlEscapedMessage(s"businessDetails.label.utr.$validBusinessTypeIdentifier"))
      }
    }

    s"redirect to obtain identifier again, when Invalid businessTypeIdentifier" in {
      val result = await(controller.showBusinessDetailsForm(Some("unCateredBusinessTypeIdentifier"))(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showBusinessTypeForm().url)
    }

    s"redirect to obtain identifier again, empty businessTypeIdentifier" in {
      val result = await(controller.showBusinessDetailsForm(None)(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showBusinessTypeForm().url)
    }
  }

  "submitBusinessDetailsForm" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request =>
      controller.submitBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))(request))

    "return a 200 response to redisplay the form with an error message for invalidly-formatted UTR" in {
      val invalidUtr = "0123456"
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> invalidUtr, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include(htmlEscapedMessage("businessDetails.title"))
      responseBody should include(htmlEscapedMessage("error.utr.invalid.length"))
      responseBody should include(invalidUtr)
      responseBody should include(validPostcode)
      noMetricExpectedAtThisPoint()
    }

    "return a 200 response to redisplay the form with an error message for UTR failing to pass Modulus11Check" in {
      val invalidUtr = "2000000001" // Modulus11Check validation fails in this case
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> invalidUtr, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include(htmlEscapedMessage("businessDetails.title"))
      responseBody should include(htmlEscapedMessage("error.utr.invalid.format"))
      responseBody should include(invalidUtr)
      responseBody should include(validPostcode)
      noMetricExpectedAtThisPoint()
    }

    "return a 200 response to redisplay the form with an error message for invalidly-formatted postcode" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> invalidPostcode)
      val result = await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include(htmlEscapedMessage("businessDetails.title"))
      responseBody should include("Enter a valid postcode, for example AA1 1AA")
      responseBody should include(validUtr.value)
      responseBody should include(invalidPostcode)
      noMetricExpectedAtThisPoint()
    }

    "return a 200 response to redisplay the form with an error message for empty form parameters" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> "", "postcode" -> "")
      val result = await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include(htmlEscapedMessage("businessDetails.title"))
      responseBody should include(htmlEscapedMessage("error.utr.blank"))
      responseBody should include("You must enter a postcode")
      noMetricExpectedAtThisPoint()
    }

    "redirect to no-agency-found page when no matching registration found by agent-subscription" in {
      withNonMatchingUtrAndPostcode(validUtr, validPostcode)
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showNoAgencyFound().url)
      metricShouldExistAndBeUpdated("Count-Subscription-NoAgencyFound")
    }

    "propagate an exception when there is no organisation name" in {
      withNoOrganisationName(validUtr, validPostcode)
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val e = intercept[IllegalStateException] {
        await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))(request))
      }
      e.getMessage should include(validUtr.value)
    }

    "showAlreadySubscribed, when fully subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = true, isSubscribedToETMP = true)
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))(request))
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showAlreadySubscribed().url)
    }

    "subscriptionComplete for partiallySubscribed User" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = false, isSubscribedToETMP = true)
      AgentSubscriptionStub.partialSubscriptionWillSucceed(CompletePartialSubscriptionBody(validUtr,
        knownFacts = SubscriptionRequestKnownFacts(validPostcode)))

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))(request))
      redirectLocation(result) shouldBe Some(routes.SubscriptionController.showSubscriptionComplete().url)
    }

    "showCreateNewAccount, creds with enrolment/s are not allowed when partiallySubscribed User" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = false, isSubscribedToETMP = true)
      AgentSubscriptionStub.partialSubscriptionWillSucceed(CompletePartialSubscriptionBody(validUtr,
        knownFacts = SubscriptionRequestKnownFacts(validPostcode)))

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))(request))
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showCreateNewAccount().url)

      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
      import scala.concurrent.ExecutionContext.Implicits.global

      await(sessionStoreService.fetchKnownFactsResult) shouldBe Some(KnownFactsResult(Utr("2000000000"), "AA1 1AA", "My Agency", isSubscribedToAgentServices = false, None, None))
    }

    "Upstream4xxResponse partialSubscriptionFix failed with 403" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = false, isSubscribedToETMP = true)
      AgentSubscriptionStub.partialSubscriptionWillReturnStatus(CompletePartialSubscriptionBody(validUtr,
        knownFacts = SubscriptionRequestKnownFacts(validPostcode)), 403)

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      an[Upstream4xxResponse] shouldBe thrownBy(await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))(request)))
    }

    "Upstream4xxResponse partialSubscriptionFix failed with 409 as someone has already been allocated the enrolment" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = false, isSubscribedToETMP = true)
      AgentSubscriptionStub.partialSubscriptionWillReturnStatus(CompletePartialSubscriptionBody(validUtr,
        knownFacts = SubscriptionRequestKnownFacts(validPostcode)), 409)

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      an[Upstream4xxResponse] shouldBe thrownBy(await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))(request)))
    }

    "Upstream5xxResponse partialSubscriptionFix failed for partiallySubscribed User" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = false, isSubscribedToETMP = true)
      AgentSubscriptionStub.partialSubscriptionWillReturnStatus(CompletePartialSubscriptionBody(validUtr,
        knownFacts = SubscriptionRequestKnownFacts(validPostcode)), 500)

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      an[Upstream5xxResponse] shouldBe thrownBy(await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationController.validBusinessTypes.head))(request)))
    }
  }

  "showCreateNewAccount" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showCreateNewAccount(request))
    behave like aPageWithFeedbackLinks(
      controller.showCreateNewAccount(_),
      authenticatedAs(subscribingCleanAgentWithoutEnrolments))

    "display the has other enrolments page if the current user is logged in and has affinity group = Agent" in {
      val result = await(controller.showCreateNewAccount(authenticatedAs(subscribingAgentEnrolledForNonMTD)))

      result should containMessages("createNewAccount.title")
    }
  }

  "showNoAgencyFound" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showNoAgencyFound(request))
    behave like aPageWithFeedbackLinks(request => {
      controller.showNoAgencyFound(request)
    }, authenticatedAs(subscribingCleanAgentWithoutEnrolments))

    "display the no agency found page if the current user is logged in and has affinity group = Agent" in {
      val result = await(controller.showNoAgencyFound(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      result should containMessages("noAgencyFound.title")
    }
  }

  "showConfirmBusinessForm" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showConfirmBusinessForm(request))

    "display the confirm your agency page if the current user is logged in and has affinity group = Agent" in {
      val utr = Utr("0123456789")
      val postcode = "AA11AA"
      val registrationName = "My Agency"

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = utr,
          postcode = postcode,
          taxpayerName = registrationName,
          isSubscribedToAgentServices = false,
          Some(businessAddress), Some("someone@example.com")))

      val result = await(controller.showConfirmBusinessForm(request))

      result should containMessages(
        "confirmBusiness.title",
        "button.back",
        "confirmBusiness.option.yes",
        "confirmBusiness.option.no")

      result should containSubstrings(s"$postcode",
        "01234 56789",
        s"$registrationName",
        s"${businessAddress.addressLine1}",
        s"${businessAddress.addressLine2.get}",
        s"${businessAddress.addressLine3.get}",
        s"${businessAddress.addressLine4.get}")
    }

    "show utr in the correct format" in {
      val utr = Utr("0123456789")
      val postcode = "AA11AA"
      val registrationName = "My Agency"

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = utr,
          postcode = postcode,
          taxpayerName = registrationName,
          isSubscribedToAgentServices = false, Some(businessAddress), Some("someone@example.com")))

      val result = await(controller.showConfirmBusinessForm(request))

      result should containSubstrings("01234 56789")
    }

    "show a back button which allows the user to return to the check-agency-status page" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withSession("businessType" -> "sole_trader")
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = Utr("0123456789"),
          postcode = "AA11AA",
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false, Some(businessAddress), Some("someone@example.com")))

      val result = await(controller.showConfirmBusinessForm(request))

      result should containSubstrings(routes.BusinessIdentificationController.submitBusinessDetailsForm(Some("sole_trader")).url)
    }

    "redirect to the Check Business Type  page if there is no KnownFactsResult in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showConfirmBusinessForm(request))

      resultShouldBeSessionDataMissing(result)
      noMetricExpectedAtThisPoint()
    }
  }

  "submitConfirmBusiness" when {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showConfirmBusinessForm(request))

    "User chooses Yes" should {
      "redirect to showAlreadySubscribed if the user is already subscribed and isSubscribedToAgentServices=true" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "yes")
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = true,
              Some(businessAddress), Some("someone@example.com")))

        val result = await(controller.submitConfirmBusinessForm(request))

        result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController.showAlreadySubscribed().url
        metricShouldExistAndBeUpdated("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
      }

      "redirect to showCheckAnswers if the user has clean creds and isSubscribedToAgentServices=false" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "yes")
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = false,
            Some(businessAddress), Some("someone@example.com")))

        val result = await(controller.submitConfirmBusinessForm(request))

        sessionStoreService.currentSession.initialDetails should not be empty

        result.header.headers(LOCATION) shouldBe routes.SubscriptionController.showCheckAnswers().url
        metricShouldExistAndBeUpdated("Count-Subscription-CleanCreds-Start")
      }
    }

    "User chooses No" should {
      "redirect to the check-agency-status page" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "no")
        sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
        utr = Utr("0123456789"),
        postcode = "AA11AA",
        taxpayerName = "My Agency",
        isSubscribedToAgentServices = false, Some(businessAddress), Some("someone@example.com")))

        val result = await(controller.submitConfirmBusinessForm(request))

        result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController.submitBusinessDetailsForm(Some("sole_trader")).url
        }
    }

    "choice is missing" should {
      "return 200 and redisplay the /confirm-your-agency page with an error message for missing choice" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "")

        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = false, Some(businessAddress), Some("someone@example.com")))

        val result = await(controller.submitConfirmBusinessForm(request))

        result should containMessages("confirmBusiness.title", "error.no-radio-selected")
      }
    }

    "form value is invalid" should {
      "result in a BadRequest" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "INVALID")
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = false, Some(businessAddress), Some("someone@example.com")))

        a[BadRequestException] shouldBe thrownBy(await(controller.submitConfirmBusinessForm(request)))
      }
    }
  }

  "showAlreadySubscribed" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showAlreadySubscribed(request))

    "display the already subscribed page if the current user is logged in and has affinity group = Agent" in {

      val result = await(controller.showAlreadySubscribed(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      result should containMessages("alreadySubscribed.title")
      val doc = Jsoup.parse(bodyOf(result))
      val signOutButton = doc.getElementById("finishSignOut")
      signOutButton.attr("href") shouldBe routes.SignedOutController.signOutWithContinueUrl.url
      signOutButton.text() shouldBe htmlEscapedMessage("button.finishSignOut")

    }
  }

  "invasive check" should {
    "return 200 and redisplay the invasiveSaAgentCodePost page with an error message for missing radio choice" in {
      val result = await(controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))
      result should containMessages("error.no-radio-selected")
    }

    "start invasiveCheck if selected Yes with SaAgentCode reference inputted" in {

      val result = await(
        controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA6012"))))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showClientDetailsForm().url)
      noMetricExpectedAtThisPoint()
    }

    "redirect to /cannot-create account if selected No" in {

      val result = await(
        controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("hasSaAgentCode", "false"))))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Declined")
    }

    "Send page back with error when failing the validation of SaAgentCode with invalid characters" in {

      val result = await(
        controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA601*2AAAA"))))

      result should containMessages("error.saAgentCode.invalid")
      noMetricExpectedAtThisPoint()
    }

    "Send page back with error when failing the validation of SaAgentCode with wrong max length" in {

      val result = await(
        controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA6012AAAA"))))

      result should containMessages("error.saAgentCode.length")
      noMetricExpectedAtThisPoint()
    }

    "Send page back with error when failing the validation of SaAgentCode with wrong min length" in {

      val result = await(
        controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA"))))

      result should containMessages("error.saAgentCode.length")
      noMetricExpectedAtThisPoint()
    }

    "Send page back with error when failing the validation of empty SaAgentCode" in {

      val result = await(
        controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", ""))))

      result should containMessages("error.saAgentCode.blank")
      noMetricExpectedAtThisPoint()
    }

    "redirect to confirm your agency when successfully submitting nino" in {
      givenNinoAGoodCombinationAndUserHasRelationshipInCesa("nino", "AA123456A", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = validUtr,
          postcode = validPostcode,
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false,
          Some(businessAddress), Some("someone@example.com")))

      val result = await(
        controller.submitClientDetailsForm(
          request
            .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123456A"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Nino("AA123456A"), true, "SA6012", agentAssurancePayeCheck)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
    }

    "redirect to confirm your agency when successfully submitting nino WITH RANDOM SPACES IN" in {
      givenNinoAGoodCombinationAndUserHasRelationshipInCesa("nino", "AA123456A", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = validUtr,
          postcode = validPostcode,
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false,
          Some(businessAddress), Some("someone@example.com")))

      val result = await(
        controller.submitClientDetailsForm(
          request
            .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA1   2 3 4 5 6        A "))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Nino("AA123456A"), true, "SA6012", agentAssurancePayeCheck)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
    }

    "redirect to invasive check start when no SACode in session to obtain it again" in {
      givenNinoAGoodCombinationAndUserHasRelationshipInCesa("nino", "AA123456A", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = validUtr,
          postcode = validPostcode,
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false,
          Some(businessAddress), Some("someone@example.com")))

      val result = await(controller.submitClientDetailsForm(request
        .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123456A"))))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.invasiveCheckStart().url)
    }

    "redirect to /cannot-create account page when submitting valid nino with no relationship" in {
      givenAUserDoesNotHaveRelationshipInCesa("nino", "AA123456A", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = validUtr,
          postcode = validPostcode,
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false, None, None))

      val result = await(
        controller.submitClientDetailsForm(
          request
            .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123456A"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Nino("AA123456A"), false, "SA6012", agentAssurancePayeCheck)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Failed")
    }

    "nino invalid send back 200 with error page" in {

      val result = await(
        controller.submitClientDetailsForm(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123"))))

      status(result) shouldBe 200
    }

    "redirect to confirm your agency when successfully submitting UTR" in {
      givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = validUtr,
          postcode = validPostcode,
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false,
          Some(businessAddress), Some("someone@example.com")))

      val result = await(
        controller.submitClientDetailsForm(
          request
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4000000009"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Utr("4000000009"), true, "SA6012", agentAssurancePayeCheck)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
    }

    "redirect to confirm your agency when successfully submitting UTR with random spaces" in {
      givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = validUtr,
          postcode = validPostcode,
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false,
          Some(businessAddress), Some("someone@example.com")))

      val result = await(
        controller.submitClientDetailsForm(
          request
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "   40000      00     009  "))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Utr("4000000009"), true, "SA6012", agentAssurancePayeCheck)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
    }

    "redirect to /cannot-create account page when submitting valid utr with no relationship" in {
      givenAUserDoesNotHaveRelationshipInCesa("utr", "40000     00  009", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = validUtr,
          postcode = validPostcode,
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false, None, None))

      val result = await(
        controller.submitClientDetailsForm(
          request
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4000000009"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Utr("4000000009"), false, "SA6012", agentAssurancePayeCheck)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Failed")
    }

    "utr blank invasiveCheck" in {

      val result = await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", ""))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.utr.blank"))
    }

    "utr invalid send back 200 with error page" in {
      val result = await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4ABC000009"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.utr.invalid.format"))
    }

    "utr wrong length" in {
      val result = await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "40000000090000000"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.utr.invalid.length"))
    }

    "Invalid form 'variant' cannot determine which option user selected  " in {
      an[BadRequestException] shouldBe thrownBy(await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("variant", "someInvalidVariant"), ("utr", "4000000009"))
            .withSession("saAgentReferenceToCheck" -> "SA6012"))))
    }

    "redirect to /cannot-create account when successfully selecting ICannotProvideEitherOfTheseDetails" in {
      givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")

      val result = await(
        controller.submitClientDetailsForm(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("variant", "cannotProvide"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Could-Not-Provide-Tax-Payer-Identifier")
    }

    "return 200 error when submitting without selected radio option" in {
      val result = await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody()
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 200
    }
  }

  def verifyAgentAssuranceAuditRequestSent(
                                            passPayeAgentAssuranceCheck: Option[Boolean],
                                            passSaAgentAssuranceCheck: Option[Boolean]): Unit = {
    val optional = Seq(
      passPayeAgentAssuranceCheck.map("passPayeAgentAssuranceCheck" -> _.toString),
      passSaAgentAssuranceCheck.map("passSaAgentAssuranceCheck" -> _.toString)).flatten

    verifyAuditRequestSent(
      1,
      AgentSubscriptionFrontendEvent.AgentAssurance,
      detail = Map(
        "utr" -> validUtr.value,
        "postcode" -> validPostcode,
        "isEnrolledSAAgent" -> "true",
        "saAgentRef" -> "FOO1234",
        //TODO "refuseToDealWith" -> ?,
        "isEnrolledPAYEAgent" -> "true",
        "payeAgentRef" -> "HZ1234",
        "authProviderId" -> "12345-credId",
        "authProviderType" -> "GovernmentGateway"
      ) ++ optional,
      tags = Map("transactionName" -> "agent-assurance", "path" -> "/")
    )
  }

  def verifyAgentAssuranceAuditRequestSentWithClientIdentifier(
                                                                identifier: TaxIdentifier,
                                                                passCESAAgentAssuranceCheck: Boolean,
                                                                saAgentRef: String,
                                                                aAssurancePayeCheck: Boolean): Unit = {

    val clientIdentifier = identifier match {
      case nino@Nino(_) => ("userEnteredNino" -> nino.value)
      case utr@Utr(_) => ("userEnteredUtr" -> utr.value)
    }
    val payeAudit = if (aAssurancePayeCheck) Seq("passPayeAgentAssuranceCheck" -> "false") else Seq.empty

    verifyAuditRequestSent(
      1,
      AgentSubscriptionFrontendEvent.AgentAssurance,
      detail = Map(
        "utr" -> validUtr.value,
        "postcode" -> validPostcode,
        "isEnrolledSAAgent" -> "false",
        "passSaAgentAssuranceCheck" -> "false",
        "isEnrolledPAYEAgent" -> "false",
        "passCESAAgentAssuranceCheck" -> passCESAAgentAssuranceCheck.toString,
        "authProviderId" -> "12345-credId",
        "authProviderType" -> "GovernmentGateway",
        "userEnteredSaAgentRef" -> saAgentRef
      ) + clientIdentifier ++ payeAudit,
      tags = Map("transactionName" -> "agent-assurance", "path" -> "/")
    )
  }
}
