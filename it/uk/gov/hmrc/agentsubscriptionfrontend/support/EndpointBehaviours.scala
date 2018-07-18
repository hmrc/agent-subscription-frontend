package uk.gov.hmrc.agentsubscriptionfrontend.support

import java.net.URLEncoder

import akka.stream.Materializer
import org.scalatest.Assertion
import org.scalatestplus.play.OneAppPerSuite
import play.api.mvc.{AnyContent, AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.routes
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub.userIsAuthenticated
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{individual, subscribingCleanAgentWithoutEnrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.binders.ContinueUrl
import uk.gov.hmrc.play.test.UnitSpec

trait EndpointBehaviours {
  me: UnitSpec with WireMockSupport with OneAppPerSuite with MetricTestSupport =>
  type PlayRequest = Request[AnyContent] => Result

  private implicit val materializer: Materializer = app.materializer

  protected def authenticatedAs(user: SampleUser): FakeRequest[AnyContentAsEmpty.type]

  protected def anAgentAffinityGroupOnlyEndpoint(doRequest: PlayRequest): Unit = {
    "redirect to the company-auth-frontend sign-in page if the current user is not logged in" in {
      AuthStub.userIsNotAuthenticated()

      val request = FakeRequest()
      val result = await(doRequest(request))

      status(result) shouldBe 303
      redirectLocation(result).get should include("/gg/sign-in")
      noMetricExpectedAtThisPoint()
    }

    "redirect to the non-Agent next steps page if the current user is logged in and does not have affinity group = Agent" in {
      val sessionKeys = AuthStub.userIsNotAnAgent(individual)

      val request = FakeRequest().withSession(sessionKeys: _*)
      val result = await(doRequest(request))

      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.StartController.showNonAgentNextSteps().url
      metricShouldExistsAndBeenUpdated("Count-Subscription-NonAgent")
    }
  }

  protected def aPageWithFeedbackLinks(action: PlayRequest, request: => Request[AnyContent] = FakeRequest()): Unit = {

    "have a 'get help with this page' link" in {
      val result = await(action(request))

      bodyOf(result) should include("Get help with this page.")
    }

    "have a beta feedback banner" in {
      val result = await(action(request))

      bodyOf(result) should include("This is a new service")
    }

    "have a beta feedback link" in {
      val result = await(action(request))

      bodyOf(result) should include("/contact/beta-feedback")
    }
  }

  private def urlencoded(toEncode: String) = URLEncoder.encode(toEncode, "UTF-8")

  private def aPageTakingContinueUrl(action: PlayRequest,
                                     sessionKeys: => Seq[(String, String)],
                                     assertContinueUrlKept: (Request[AnyContent], Result, String) => Assertion,
                                     assertContinueUrlNotKept: (Request[AnyContent], Result, Option[String]) => Assertion): Unit = {
    def doRequestWithContinueUrl(continueUrl: String) = {
      val request = FakeRequest("GET", s"?continue=${urlencoded(continueUrl)}").withSession(sessionKeys: _*)
      val result = await(action(request))
      (request, result)
    }

    "include absolute continue URL" in {
      val url = "http://localhost"
      val (request, result) = doRequestWithContinueUrl(url)
      assertContinueUrlKept(request, result, url)
    }

    "include relative continue URL" in {
      val url = "/foo"
      val (request, result) = doRequestWithContinueUrl(url)
      assertContinueUrlKept(request, result, url)
    }

    "include continue URL if it's the absolute www.tax.service.gov.uk continue url" in {
      val url = "http://www.tax.service.gov.uk/foo/bar?some=true"
      val (request, result) = doRequestWithContinueUrl(url)
      assertContinueUrlKept(request, result, url)
    }

    "include continue URL if it's whitelisted" in {
      val url = "http://www.foo.com/bar?some=false"
      val (request, result) = doRequestWithContinueUrl(url)
      assertContinueUrlKept(request, result, url)
    }

    "not include a continue URL if it's not whitelisted" in {
      val url = "http://www.foo.org/bar?some=false"
      val (request, result) = doRequestWithContinueUrl(url)
      assertContinueUrlNotKept(request, result, Some(url))
    }

    "not include a continue URL if it contains an invalid character" in {
      val url = "http://www@foo.com"
      val (request, result) = doRequestWithContinueUrl(url)
      assertContinueUrlNotKept(request, result, Some(url))
    }

    "not include a continue URL if it's not provided" in {
      val request = FakeRequest("GET", "/").withSession(sessionKeys: _*)
      val result = await(action(request))
      assertContinueUrlNotKept(request, result, None)
    }
  }

  protected def anEndpointTakingContinueUrlAndRedirectingWithIt(action: PlayRequest): Unit = {
    def continueParamRegex(expectedContinueUrl: String) = s"[\\?&]continue=${urlencoded(expectedContinueUrl)}".r

    def checkRedirectHasContinueUrl(request: Request[AnyContent], result: Result, expectedContinueUrl: String) = {
      status(result) shouldBe 303
      result.header.headers(LOCATION) should include regex continueParamRegex(expectedContinueUrl)
    }

    def checkRedirectHasNoContinueUrl(request: Request[AnyContent], result: Result, expectedContinueUrl: Option[String]) = {
      status(result) shouldBe 303
      result.header.headers(LOCATION) should not include "continue="
    }

    aPageTakingContinueUrl(action, Seq(), checkRedirectHasContinueUrl, checkRedirectHasNoContinueUrl)
  }

  protected def aPageTakingContinueUrlAndContainingItAsALink(action: PlayRequest): Unit = {
    def checkResultHasLinkWithContinueUrl(request: Request[AnyContent], result: Result, expectedContinueUrl: String) = {
      status(result) shouldBe 200
      bodyOf(result) should include regex(s""".*<a .*href=.*\\?continue=${urlencoded(expectedContinueUrl)}.*</a>""".r)
    }

    def checkResultHasNoLinkWithContinueUrl(request: Request[AnyContent], result: Result, expectedContinueUrl: Option[String]) = {
      status(result) shouldBe 200
      expectedContinueUrl match {
        case Some(continueUrl) =>
          bodyOf(result) should not include regex(s""".*<a .*href=.*\\?continue=${urlencoded(continueUrl)}.*</a>""".r)
        case None =>
          bodyOf(result) should not include regex(s""".*<a .*href=.*\\?continue=.*</a>""".r)
      }
    }

    aPageTakingContinueUrl(action, Seq(), checkResultHasLinkWithContinueUrl, checkResultHasNoLinkWithContinueUrl)
  }

  protected def aPageTakingContinueUrlAndCachingInSessionStore(action: PlayRequest, sessionStoreService: TestSessionStoreService, sessionKeys: => Seq[(String, String)]): Unit = {
    def hc(request: Request[AnyContent]) = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    def checkContinueUrlIsInCache(request: Request[AnyContent], result: Result, expectedContinueUrl: String) = {
      status(result) shouldBe 200
      withClue("ContinueUrl was not found in session store, actual: ") {
        sessionStoreService.currentSession(hc(request)).continueUrl shouldBe Some(ContinueUrl(expectedContinueUrl))
      }
    }

    def checkContinueUrlIsNotInCache(request: Request[AnyContent], result: Result, expectedContinueUrl: Option[String]) = {
      status(result) shouldBe 200
      withClue("A ContinueUrl was found in session store, it was: ") {
        sessionStoreService.currentSession(hc(request)).continueUrl shouldBe None
      }
    }

    aPageTakingContinueUrl(action, sessionKeys, checkContinueUrlIsInCache, checkContinueUrlIsNotInCache)
  }
}
