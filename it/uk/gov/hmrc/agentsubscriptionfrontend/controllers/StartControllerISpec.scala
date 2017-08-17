package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.net.URLEncoder

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.repository.KnownFactsResultMongoRepository
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.ExecutionContext.Implicits.global

class StartControllerISpec extends BaseISpec {

  private lazy val controller: StartController = app.injector.instanceOf[StartController]
  private lazy val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"
  private lazy val repo = app.injector.instanceOf[KnownFactsResultMongoRepository]

  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder
    .configure("government-gateway.url" -> configuredGovernmentGatewayUrl)

  "context root" should {
    "redirect to start page" in {
      implicit val request = FakeRequest()
      val result = await(controller.root(request))

      status(result) shouldBe 303
      redirectLocation(result).head should include("/start")

      sessionStoreService.currentSession.continueUrl shouldBe None
    }

    "store the absolute continue url in the session store" in {
      val url = "http://localhost"
      implicit val request = FakeRequest(GET, s"?continue=$url")

      val result = await(controller.root(request))

      status(result) shouldBe 303
      redirectLocation(result).head should include("/start")

      sessionStoreService.currentSession.continueUrl shouldBe Some(ContinueUrl(url))
    }
  }

  "start" should {
    "not require authentication" in {
      AuthStub.userIsNotAuthenticated()

      val result = await(controller.start(FakeRequest()))

      status(result) shouldBe 200
    }

    "be available" in {
      val result = await(controller.start()(FakeRequest()))

      bodyOf(result) should include("Create your Agent Services account")
    }

    "store the absolute continue url in the session store" in {
      val url = "http://localhost"
      implicit val request = FakeRequest(GET, s"?continue=$url")

      val result = await(controller.start(request))

      status(result) shouldBe 200
      bodyOf(result) should include("Create your Agent Services account")

      sessionStoreService.currentSession.continueUrl shouldBe Some(ContinueUrl(url))
    }

    "store the relative continue url in the session store" in {
      val url = "/foo"
      implicit val request = FakeRequest(GET, s"?continue=$url")

      val result = await(controller.start(request))

      status(result) shouldBe 200
      bodyOf(result) should include("Create your Agent Services account")

      sessionStoreService.currentSession.continueUrl shouldBe Some(ContinueUrl(url))
    }

    "store the absolute www.tax.service.gov.uk continue url in the session store" in {
      val url = "http://www.tax.service.gov.uk/foo/bar?some=true"
      implicit val request = FakeRequest(GET, s"?continue=${URLEncoder.encode(url, "UTF-8")}")

      val result = await(controller.start(request))

      status(result) shouldBe 200
      bodyOf(result) should include("Create your Agent Services account")

      sessionStoreService.currentSession.continueUrl shouldBe Some(ContinueUrl(url))
    }

    "store the whitelisted absolute external continue url in the session store" in {
      val url = "http://www.foo.com/bar?some=false"
      implicit val request = FakeRequest(GET, s"?continue=${URLEncoder.encode(url, "UTF-8")}")

      val result = await(controller.start(request))

      status(result) shouldBe 200
      bodyOf(result) should include("Create your Agent Services account")

      sessionStoreService.currentSession.continueUrl shouldBe Some(ContinueUrl(url))
    }

    "not store the non whitelisted absolute external continue url in the session store" in {
      val url = "http://www.foo.org/bar?some=false"
      implicit val request = FakeRequest(GET, s"?continue=${URLEncoder.encode(url, "UTF-8")}")

      val result = await(controller.start(request))

      status(result) shouldBe 200
      bodyOf(result) should include("Create your Agent Services account")

      sessionStoreService.currentSession.continueUrl shouldBe None
    }

    "not store the absolute external continue url in the session store if the url contains an invalid character" in {
      val url = "http://www@foo.com"
      implicit val request = FakeRequest(GET, s"?continue=${URLEncoder.encode(url, "UTF-8")}")

      val result = await(controller.start(request))

      status(result) shouldBe 200
      bodyOf(result) should include("Create your Agent Services account")

      sessionStoreService.currentSession.continueUrl shouldBe None
    }

    "not store the continue url in the session store if the continue param is not mentioned in the url" in {
      implicit val request = FakeRequest()

      val result = await(controller.start(request))

      status(result) shouldBe 200
      bodyOf(result) should include("Create your Agent Services account")

      sessionStoreService.currentSession.continueUrl shouldBe None
    }

    behave like aPageWithFeedbackLinks(request => controller.start(request))

  }

  "showNonAgentNextSteps" when {
    "the current user is logged in" should {

      "display the non-agent next steps page"  in {
        implicit val request = authenticatedRequest()
        val result = await(controller.showNonAgentNextSteps(request))

        status(result) shouldBe OK
        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")
        bodyOf(result) should include(htmlEscapedMessage("nonAgent.title"))
      }

      "include link to create new account" in {
        val result = await(controller.showNonAgentNextSteps(authenticatedRequest()))

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

    behave like aPageWithFeedbackLinks(request => controller.showNonAgentNextSteps(request), authenticatedRequest())
  }

  "returnAfterGGCredsCreated" should {
    "redirect to the subscription-details page if given a valid KnownFactsResult ID" in {
      val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
      val persistedId = await(repo.create(knownFactsResult))

      val result = await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include ("/subscription-details")
    }

    "redirect to the check-agency-status page if given an invalid KnownFactsResult ID" in {
      val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
      val persistedId = await(repo.create(knownFactsResult))
      val invalidId = s"A$persistedId"

      val result = await(controller.returnAfterGGCredsCreated(id = Some(invalidId))(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include ("/check-agency-status")
    }

    "redirect to check-agency-status page if there is no valid KnownFactsResult ID" in {
      val result = await(controller.returnAfterGGCredsCreated(id = None)(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include ("/check-agency-status")
    }

    "delete the persisted KnownFactsResult if given a valid KnownFactsResult ID" in {
      val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
      val persistedId = await(repo.create(knownFactsResult))

      await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest()))

      await(repo.findKnownFactsResult(persistedId)) shouldBe None
    }

    "repopulate the KnownFacts session store with the persisted KnownFactsResult, if given a valid KnownFactsResult ID" in {
      val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
      val persistedId = await(repo.create(knownFactsResult))
      implicit val request = FakeRequest()

      await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(request))

      sessionStoreService.currentSession.knownFactsResult shouldBe Some(knownFactsResult)
    }

    "place a provided continue URL in session store, if given a valid KnownFactsResult ID" in {
      val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
      val persistedId = await(repo.create(knownFactsResult))
      val continueUrl = ContinueUrl("/test-continue-url")
      implicit val request = FakeRequest(GET, s"?id=$persistedId&continue=${continueUrl.encodedUrl}")

      await(controller.returnAfterGGCredsCreated()(request))

      sessionStoreService.currentSession.continueUrl shouldBe Some(continueUrl)
    }
  }
}
