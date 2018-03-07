package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.test.Helpers._
import play.api.test.Helpers.redirectLocation
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AgentSubscriptionFrontendEvent
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub.givenUtrIsNotForbidden
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.withMatchingUtrAndPostcode
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub.{hasNoEnrolments, isEnrolledForNonMtdServices}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers.subscribingAgent

class CheckAgencyControllerWithoutAssuranceFlagISpec extends CheckAgencyControllerISpec {

  override val agentAssuranceFlag: Boolean =  false

  "checkAgencyStatus with the agentAssuranceFlag set to false" should {
    "redirect to confirm agency page and store known facts result in the session store when a matching registration is found for the UTR and postcode" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUtrIsNotForbidden(validUtr.value)


      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)

      sessionStoreService.currentSession.knownFactsResult shouldBe
        Some(KnownFactsResult(validUtr, validPostcode, "My Agency", isSubscribedToAgentServices = false))
      verifyAuditRequestNotSent(AgentSubscriptionFrontendEvent.AgentAssurance)
    }

    "store isSubscribedToAgentServices = false in session when the business registration found by agent-subscription is not already subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUtrIsNotForbidden(validUtr.value)


      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
      sessionStoreService.currentSession.knownFactsResult.get.isSubscribedToAgentServices shouldBe false
      verifyAuditRequestNotSent(AgentSubscriptionFrontendEvent.AgentAssurance)
    }

    "redirect to already subscribed page when the business registration found by agent-subscription is already subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = true)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUtrIsNotForbidden(validUtr.value)


      implicit val request = authenticatedRequest()
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showAlreadySubscribed().url)
      verifyAuditRequestNotSent(AgentSubscriptionFrontendEvent.AgentAssurance)
    }
  }
}
