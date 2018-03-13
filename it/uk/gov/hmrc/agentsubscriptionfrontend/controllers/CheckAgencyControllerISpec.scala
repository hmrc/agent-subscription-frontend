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
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AgentSubscriptionFrontendEvent
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}

trait CheckAgencyControllerISpec extends BaseISpec with SessionDataMissingSpec {
  val validUtr = Utr("2000000000")
  val validPostcode = "AA1 1AA"
  private val invalidPostcode = "not a postcode"

  val utr = Utr("0123456789")
  val postcode = "AA11AA"
  val registrationName = "My Agency"

  def agentAssuranceFlag: Boolean

  private lazy val redirectUrl: String = "http://localhost:9401/agent-services-account"

  private lazy val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"

  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder
    .configure("agentAssuranceFlag" -> agentAssuranceFlag,
      "government-gateway.url" -> configuredGovernmentGatewayUrl)

  lazy val controller: CheckAgencyController = app.injector.instanceOf[CheckAgencyController]

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
      metricShouldExistsAndBeenUpdated("Count-Subscription-CheckAgency-Start")
    }

    "display the AS Account Page if the current user has HMRC-AS-AGENT enrolment" in {
      isSubscribedToMtd(subscribingAgent)

      val result = await(controller.showCheckAgencyStatus(authenticatedRequest()))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(redirectUrl)
      metricShouldExistsAndBeenUpdated("Count-Subscription-AlreadySubscribed-HasEnrolment-AgentServicesAccount")
    }

    "redirect to Already Subscribed page if current user has HMRC-AS-AGENT enrolment" in {
      isSubscribedToMtdNotActivated(subscribingAgent)

      val result = await(controller.showCheckAgencyStatus(authenticatedRequest()))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showAlreadySubscribed().url)
      metricShouldExistsAndBeenUpdated("Count-Subscription-AlreadySubscribed-HasEnrolment")
    }
  }

  "checkAgencyStatus" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.checkAgencyStatus(request))

    "return a 200 response to redisplay the form with an error message for invalidly-formatted UTR" in {
      val invalidUtr = "0123456"
      isEnrolledForNonMtdServices(subscribingAgent)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> invalidUtr, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include("Identify your business")
      responseBody should include("Enter a valid 10-digit UTR")
      responseBody should include(invalidUtr)
      responseBody should include(validPostcode)
      noMetricExpectedAtThisPoint()
    }

    "return a 200 response to redisplay the form with an error message for UTR failing to pass Modulus11Check" in {
      val invalidUtr = "2000000001" // Modulus11Check validation fails in this case
      isEnrolledForNonMtdServices(subscribingAgent)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> invalidUtr, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include("Identify your business")
      responseBody should include("Enter a valid 10-digit UTR")
      responseBody should include(invalidUtr)
      responseBody should include(validPostcode)
      noMetricExpectedAtThisPoint()
    }

    "return a 200 response to redisplay the form with an error message for invalidly-formatted postcode" in {
      isEnrolledForNonMtdServices(subscribingAgent)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> invalidPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include("Identify your business")
      responseBody should include("Enter a valid postcode, for example AA1 1AA")
      responseBody should include(validUtr.value)
      responseBody should include(invalidPostcode)
      noMetricExpectedAtThisPoint()
    }

    "return a 200 response to redisplay the form with an error message for empty form parameters" in {
      isEnrolledForNonMtdServices(subscribingAgent)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> "", "postcode" -> "")
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include("Identify your business")
      responseBody should include("You must enter a UTR or reference")
      responseBody should include("You must enter a postcode")
      noMetricExpectedAtThisPoint()
    }

    "redirect to no-agency-found page when no matching registration found by agent-subscription" in {
      isEnrolledForNonMtdServices(subscribingAgent)
      withNonMatchingUtrAndPostcode(validUtr, validPostcode)
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showNoAgencyFound().url)
      metricShouldExistsAndBeenUpdated("Count-Subscription-NoAgencyFound")
    }

    "propagate an exception when there is no organisation name" in {
      withNoOrganisationName(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
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
        s"$postcode", s"${utr.value}", s"$registrationName")
      metricShouldExistsAndBeenUpdated("Count-Subscription-CleanCreds-Start")
    }

    "show a button which allows the user to return to Check Agency Status page" in {
      isEnrolledForNonMtdServices(subscribingAgent)
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(utr = Utr("0123456789"), postcode = "AA11AA", taxpayerName = "My Agency", isSubscribedToAgentServices = false))

      val result = await(controller.showConfirmYourAgency(request))

      checkHtmlResultWithBodyText(result, routes.CheckAgencyController.showCheckAgencyStatus().url)
      metricShouldExistsAndBeenUpdated("Count-Subscription-CleanCreds-Start")
    }

    "show a Continue button which allows the user to go to Subscription Details if isSubscribedToAgentServices=false" in {
      isEnrolledForNonMtdServices(subscribingAgent)
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(utr = Utr("0123456789"), postcode = "AA11AA", taxpayerName = "My Agency", isSubscribedToAgentServices = false))

      val result = await(controller.showConfirmYourAgency(request))

      checkHtmlResultWithBodyText(result, routes.SubscriptionController.showInitialDetails().url)
      metricShouldExistsAndBeenUpdated("Count-Subscription-CleanCreds-Start")
    }

    "show a Continue button which allows the user to go to Already Subscribed if isSubscribedToAgentServices=true" in {
      isEnrolledForNonMtdServices(subscribingAgent)
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(utr = Utr("0123456789"), postcode = "AA11AA", taxpayerName = "My Agency", isSubscribedToAgentServices = true))

      val result = await(controller.showConfirmYourAgency(request))

      checkHtmlResultWithBodyText(result, routes.CheckAgencyController.showAlreadySubscribed().url)
      metricShouldExistsAndBeenUpdated("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
    }

    "redirect to the Check Agency Status page if there is no KnownFactsResult in session because the user has returned to a bookmark" in {
      isEnrolledForNonMtdServices(subscribingAgent)
      implicit val request = authenticatedRequest()

      val result = await(controller.showConfirmYourAgency(request))

      resultShouldBeSessionDataMissing(result)
      noMetricExpectedAtThisPoint()
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

  "invasive check" should {
    "start invasiveCheck if selected Yes with SaAgentCode reference inputted" in {
      hasNoEnrolments(subscribingAgent) // validate what kind of agent

      val result = await(controller.invasiveSaAgentCodePost(authenticatedRequest()
        .withFormUrlEncodedBody(("confirmResponse", "true"), ("confirmResponse-true-hidden-input", "SA6012"))))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.invasiveTaxPayerOptionGet().url)
      noMetricExpectedAtThisPoint()
    }
    "redirect to setup incomplete if selected No" in {
      hasNoEnrolments(subscribingAgent)

      val result = await(controller.invasiveSaAgentCodePost(authenticatedRequest()
        .withFormUrlEncodedBody(("confirmResponse", "false"))))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.setupIncomplete().url)
      metricShouldExistsAndBeenUpdated("Count-Subscription-InvasiveCheck-Declined")
    }

    "Send page back with error when failing the validation of SaAgentCode" in {
      hasNoEnrolments(subscribingAgent)

      val result = await(controller.invasiveSaAgentCodePost(authenticatedRequest()
        .withFormUrlEncodedBody(("confirmResponse", "true"), ("confirmResponse-true-hidden-input", "SA6012AAAA"))))

      status(result) shouldBe 200
      checkHtmlResultWithBodyText(result, htmlEscapedMessage("error.saAgentCode.invalid"))
      noMetricExpectedAtThisPoint()
    }

    "redirect to confirm your agency when successfully submitting nino" in {
      givenNinoAGoodCombinationAndUserHasRelationshipInCesa("nino", "AA123456A", "SA6012")
      hasNoEnrolments(subscribingAgent)

      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(utr = validUtr, postcode = validPostcode, taxpayerName = "My Agency", isSubscribedToAgentServices = false))

      val result = await(controller.invasiveTaxPayerOption(request
        .withFormUrlEncodedBody(("confirmResponse", "true"), ("confirmResponse-true-hidden-input", "AA123456A"))
        .withSession(("saAgentReferenceToCheck" -> "SA6012"))))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Nino("AA123456A"), true, "SA6012")
      metricShouldExistsAndBeenUpdated("Count-Subscription-InvasiveCheck-Success")
    }

    "redirect to setup incomplete page when submitting valid nino with no relationship" in {
      givenAUserDoesNotHaveRelationshipInCesa("nino", "AA123456A", "SA6012")
      hasNoEnrolments(subscribingAgent)

      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(utr = validUtr, postcode = validPostcode, taxpayerName = "My Agency", isSubscribedToAgentServices = false))

      val result = await(controller.invasiveTaxPayerOption(request
        .withFormUrlEncodedBody(("confirmResponse", "true"), ("confirmResponse-true-hidden-input", "AA123456A"))
        .withSession(("saAgentReferenceToCheck" -> "SA6012"))))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.setupIncomplete().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Nino("AA123456A"), false, "SA6012")
      metricShouldExistsAndBeenUpdated("Count-Subscription-InvasiveCheck-Failed")
    }

    "nino invalid send back 200 with error page" in {
      hasNoEnrolments(subscribingAgent)

      val result = await(controller.invasiveTaxPayerOption(authenticatedRequest()
        .withFormUrlEncodedBody(("confirmResponse", "true"), ("confirmResponse-true-hidden-input", "AA123"))))

      status(result) shouldBe 200
    }

    "redirect to confirm your agency when successfully submitting UTR" in {
      givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")
      hasNoEnrolments(subscribingAgent)

      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(utr = validUtr, postcode = validPostcode, taxpayerName = "My Agency", isSubscribedToAgentServices = false))

      val result = await(controller.invasiveTaxPayerOption(request
        .withFormUrlEncodedBody(("confirmResponse", "false"), ("confirmResponse-false-hidden-input", "4000000009"))
        .withSession(("saAgentReferenceToCheck" -> "SA6012"))))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Utr("4000000009"), true, "SA6012")
      metricShouldExistsAndBeenUpdated("Count-Subscription-InvasiveCheck-Success")
    }

    "redirect to setup incomplete page when submitting valid utr with no relationship" in {
      givenAUserDoesNotHaveRelationshipInCesa("utr", "4000000009", "SA6012")
      hasNoEnrolments(subscribingAgent)

      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(utr = validUtr, postcode = validPostcode, taxpayerName = "My Agency", isSubscribedToAgentServices = false))

      val result = await(controller.invasiveTaxPayerOption(request
        .withFormUrlEncodedBody(("confirmResponse", "false"), ("confirmResponse-false-hidden-input", "4000000009"))
        .withSession(("saAgentReferenceToCheck" -> "SA6012"))))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.setupIncomplete().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Utr("4000000009"), false, "SA6012")
      metricShouldExistsAndBeenUpdated("Count-Subscription-InvasiveCheck-Failed")
    }

    "utr invalid send back 200 with error page" in {
      hasNoEnrolments(subscribingAgent)

      val result = await(controller.invasiveTaxPayerOption(authenticatedRequest()
        .withFormUrlEncodedBody(("confirmResponse", "false"), ("confirmResponse-false-hidden-input", "42123"))
        .withSession(("saAgentReferenceToCheck" -> "SA6012"))))

      status(result) shouldBe 200
    }

    "return 200 error when submitting without selected radio option" in {
      hasNoEnrolments(subscribingAgent)

      val result = await(controller.invasiveTaxPayerOption(authenticatedRequest()
        .withFormUrlEncodedBody()
        .withSession(("saAgentReferenceToCheck" -> "SA6012"))))

      status(result) shouldBe 200
    }
  }


  def verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck: Option[Boolean], passSaAgentAssuranceCheck: Option[Boolean]): Unit = {
    val optional = Seq(
      passPayeAgentAssuranceCheck.map("passPayeAgentAssuranceCheck" -> _.toString),
      passSaAgentAssuranceCheck.map("passSaAgentAssuranceCheck" -> _.toString)
    ).flatten

    verifyAuditRequestSent(1, AgentSubscriptionFrontendEvent.AgentAssurance,
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
      tags = Map(
        "transactionName" -> "agent-assurance",
        "path" -> "/"
      )
    )
  }

  def verifyAgentAssuranceAuditRequestSentWithClientIdentifier(identifier: TaxIdentifier, passCESAAgentAssuranceCheck: Boolean, saAgentRef: String): Unit = {
    val clientIdentifier = identifier match {
      case nino @ Nino(_) => ("userEnteredNino" -> nino.value)
      case utr @ Utr(_) => ("userEnteredUtr" -> utr.value)
    }
    verifyAuditRequestSent(1, AgentSubscriptionFrontendEvent.AgentAssurance,
      detail = Map(
        "utr" -> validUtr.value,
        "postcode" -> validPostcode,
        "isEnrolledSAAgent" -> "false",
        "passSaAgentAssuranceCheck" -> "false",
        "isEnrolledPAYEAgent" -> "false",
        "passPayeAgentAssuranceCheck" -> "false",
        "passCESAAgentAssuranceCheck" -> passCESAAgentAssuranceCheck.toString,
        "authProviderId" -> "12345-credId",
        "authProviderType" -> "GovernmentGateway",
        "userEnteredSaAgentRef" -> saAgentRef
      ) + clientIdentifier,
      tags = Map(
        "transactionName" -> "agent-assurance",
        "path" -> "/"
      )
    )
  }


}