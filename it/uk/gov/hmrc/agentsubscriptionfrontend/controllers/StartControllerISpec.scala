package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.net.URLEncoder

import org.jsoup.Jsoup
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.{CompletePartialSubscriptionBody, KnownFactsResult, SubscriptionRequestKnownFacts}
import uk.gov.hmrc.agentsubscriptionfrontend.repository.KnownFactsResultMongoRepository
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{individual, subscribingAgentEnrolledForHMRCASAGENT}
import uk.gov.hmrc.play.binders.ContinueUrl
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub
import uk.gov.hmrc.http.{Upstream4xxResponse, Upstream5xxResponse}

import scala.concurrent.ExecutionContext.Implicits.global

class StartControllerISpec extends BaseISpec {

  private lazy val controller: StartController = app.injector.instanceOf[StartController]
  private lazy val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"
  private lazy val repo = app.injector.instanceOf[KnownFactsResultMongoRepository]

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder
      .configure("government-gateway.url" -> configuredGovernmentGatewayUrl)

  "context root" should {
    "redirect to start page" in {
      implicit val request = FakeRequest()
      val result = await(controller.root(request))

      status(result) shouldBe 303
      redirectLocation(result).head should include(routes.StartController.start().url)
    }

    behave like anEndpointTakingContinueUrlAndRedirectingWithIt(controller.root(_))
  }

  "start" should {
    "not require authentication" in {
      AuthStub.userIsNotAuthenticated()

      val result = await(controller.start(FakeRequest()))

      status(result) shouldBe 200
    }

    "be available" in {
      val result = await(controller.start()(FakeRequest()))

      bodyOf(result) should include("Agent services account: sign in or set up")
    }

    "contain a start button pointing to /check-business-type" in {
      val result = await(controller.start(FakeRequest()))
      val doc = Jsoup.parse(bodyOf(result))
      val startLink = doc.getElementById("start")
      startLink.attr("href") shouldBe routes.CheckAgencyController.showCheckBusinessType().url
      startLink.text() shouldBe htmlEscapedMessage("startpage.continue")
    }

    behave like aPageWithFeedbackLinks(request => controller.start(request))

    behave like aPageTakingContinueUrlAndContainingItAsALink(request => controller.start(request))
  }

  "showNonAgentNextSteps" when {
    "the current user is logged in" should {

      "display the non-agent next steps page" in {
        implicit val request = authenticatedAs(individual)
        val result = await(controller.showNonAgentNextSteps(request))

        status(result) shouldBe OK
        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")
        bodyOf(result) should include(htmlEscapedMessage("nonAgent.title"))
      }

      "include link to create new account" in {
        val result = await(controller.showNonAgentNextSteps(authenticatedAs(individual)))

        status(result) shouldBe 200
        bodyOf(result) should include("/redirect-to-sos")
      }
    }

    "the current user is not logged in" should {
      "redirect to the company-auth-frontend sign-in page" in {
        AuthStub.userIsNotAuthenticated()

        val request = FakeRequest()
        val result = await(controller.showNonAgentNextSteps(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).head should include("gg/sign-in")
      }
    }

    behave like aPageWithFeedbackLinks(
      request => controller.showNonAgentNextSteps(request),
      authenticatedAs(individual))
  }

  "returnAfterGGCredsCreated" should {
    trait ValidKnownFactsCached {
      val knownFactsResult =
        KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = false)
      val persistedId = await(repo.create(knownFactsResult))
    }

    "redirect to the /subscription-details page if given a valid KnownFactsResult ID" when {
      "agent is unsubscribed" in new ValidKnownFactsCached {
        AgentSubscriptionStub.withMatchingUtrAndPostcode(
          knownFactsResult.utr,
          knownFactsResult.postcode,
          isSubscribedToAgentServices = false,
          isSubscribedToETMP = false)

        val result = await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest()))

        status(result) shouldBe 303
        redirectLocation(result).head should include(routes.SubscriptionController.showInitialDetails().url)
      }

      "agent is already fully subscribed" in new ValidKnownFactsCached {
        AgentSubscriptionStub.withMatchingUtrAndPostcode(
          knownFactsResult.utr,
          knownFactsResult.postcode,
          isSubscribedToAgentServices = true,
          isSubscribedToETMP = true)

        val result = await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest()))

        status(result) shouldBe 303
        redirectLocation(result).head should include(routes.SubscriptionController.showInitialDetails().url)
      }
    }

    "redirect to /subscription-complete if given a valid KnownFactsResult ID and agent is partially subscribed (subscribed in ETMP but not enrolled)" in new ValidKnownFactsCached {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(
        knownFactsResult.utr,
        knownFactsResult.postcode,
        isSubscribedToAgentServices = false,
        isSubscribedToETMP = true)

      AgentSubscriptionStub.partialSubscriptionWillSucceed(CompletePartialSubscriptionBody(utr = knownFactsResult.utr,
        knownFacts = SubscriptionRequestKnownFacts(knownFactsResult.postcode)))

      val result = await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include(routes.SubscriptionController.showSubscriptionComplete().url)
    }

    "throw Upstream4xxResponse if agent-subscription returns 403 when completing partial subscription" in new ValidKnownFactsCached {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(
        knownFactsResult.utr,
        knownFactsResult.postcode,
        isSubscribedToAgentServices = false,
        isSubscribedToETMP = true)

      AgentSubscriptionStub.partialSubscriptionWillReturnStatus(CompletePartialSubscriptionBody(utr = knownFactsResult.utr,
        knownFacts = SubscriptionRequestKnownFacts(knownFactsResult.postcode)), 403)

      an[Upstream4xxResponse] shouldBe thrownBy(await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest())))
    }

    "throw Upstream4xxResponse if agent-subscription returns 409 when completing partial subscription" in new ValidKnownFactsCached {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(
        knownFactsResult.utr,
        knownFactsResult.postcode,
        isSubscribedToAgentServices = false,
        isSubscribedToETMP = true)

      AgentSubscriptionStub.partialSubscriptionWillReturnStatus(CompletePartialSubscriptionBody(utr = knownFactsResult.utr,
        knownFacts = SubscriptionRequestKnownFacts(knownFactsResult.postcode)), 409)

      an[Upstream4xxResponse] shouldBe thrownBy(await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest())))
    }

    "throw Upstream5xxResponse, 500 when executing partialSubscriptionFix" in new ValidKnownFactsCached {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(
        knownFactsResult.utr,
        knownFactsResult.postcode,
        isSubscribedToAgentServices = false,
        isSubscribedToETMP = true)

      AgentSubscriptionStub.partialSubscriptionWillReturnStatus(CompletePartialSubscriptionBody(utr = knownFactsResult.utr,
        knownFacts = SubscriptionRequestKnownFacts(knownFactsResult.postcode)), 500)

      an[Upstream5xxResponse] shouldBe thrownBy(await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest())))
    }

    "redirect to the /check-business-type page if given an invalid KnownFactsResult ID" in new ValidKnownFactsCached {
      val invalidId = s"A$persistedId"

      val result = await(controller.returnAfterGGCredsCreated(id = Some(invalidId))(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include(routes.CheckAgencyController.showCheckBusinessType().url)
    }

    "redirect to /check-business-type page if there is no valid KnownFactsResult ID" in {
      val result = await(controller.returnAfterGGCredsCreated(id = None)(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include(routes.CheckAgencyController.showCheckBusinessType().url)
    }

    "delete the persisted KnownFactsResult if given a valid KnownFactsResult ID" in new ValidKnownFactsCached {
      await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest()))

      await(repo.findKnownFactsResult(persistedId)) shouldBe None
    }

    "repopulate the KnownFacts session store with the persisted KnownFactsResult, if given a valid KnownFactsResult ID" in new ValidKnownFactsCached {
      implicit val request = FakeRequest()

      await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(request))

      sessionStoreService.currentSession.knownFactsResult shouldBe Some(knownFactsResult)
    }

    "place a provided continue URL in session store, if given a valid KnownFactsResult ID" in new ValidKnownFactsCached {
      val continueUrl = ContinueUrl("/test-continue-url")
      implicit val request = FakeRequest(GET, s"?id=$persistedId&continue=${continueUrl.encodedUrl}")

      await(controller.returnAfterGGCredsCreated()(request))

      sessionStoreService.currentSession.continueUrl shouldBe Some(continueUrl)
    }
  }
}