package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.givenSubscriptionJourneyRecordExists
import uk.gov.hmrc.agentsubscriptionfrontend.support.UnitSpecIt

import scala.concurrent.Future

// these are not stand-alone tests, they are designed to be called with 'behave like' from other specs
trait EmailVerificationBehaviours { this: UnitSpecIt =>

  def checksIfEmailIsVerified(sjr: SubscriptionJourneyRecord, isExpectedResult: Result => Boolean)(f: () => Future[Result]) = {

    "behave normally if the user has not yet made a decision which email address to use" in {
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        sjr.copy(
          contactEmailData = None,
          verifiedEmails = Set.empty
        )
      )
      val result = await(f())
      isExpectedResult(result) shouldBe true
    }

    "redirect to email verification if the user has chosen to use business email but it is not verified" in {
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        sjr.copy(
          contactEmailData = Some(ContactEmailData(useBusinessEmail = true, contactEmail = None)),
          verifiedEmails = Set.empty
        )
      )
      val result = await(f())
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.EmailVerificationController.verifyEmail().url)
    }

    "redirect to email verification if the user has chosen to use a different email that is not yet verified" in {
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        sjr.copy(
          contactEmailData = Some(ContactEmailData(useBusinessEmail = false, contactEmail = Some("other@email.com"))),
          // the business email is verified, but the new one is not
          verifiedEmails = Set(sjr.businessDetails.registration.get.emailAddress.get)
        )
      )
      val result = await(f())
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.EmailVerificationController.verifyEmail().url)
    }

    "behave normally if the user has chosen to use business email, and it is already verified" in {
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        sjr.copy(
          contactEmailData = Some(ContactEmailData(useBusinessEmail = true, contactEmail = None)),
          verifiedEmails = Set(sjr.businessDetails.registration.get.emailAddress.get)
        )
      )
      val result = await(f())
      isExpectedResult(result) shouldBe true
    }

    "behave normally if the user has chosen to use a different email, which has already been verified" in {
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        sjr.copy(
          contactEmailData = Some(ContactEmailData(useBusinessEmail = false, contactEmail = Some("other@email.com"))),
          verifiedEmails = Set("other@email.com")
        )
      )
      val result = await(f())
      isExpectedResult(result) shouldBe true
    }
  }
}
