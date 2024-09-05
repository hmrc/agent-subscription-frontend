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

package uk.gov.hmrc.agentsubscriptionfrontend.support

import org.scalatest.Assertion
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.routes
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessType}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AuthStub, SsoStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.individual
import uk.gov.hmrc.http.SessionKeys

import java.net.URLEncoder
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait EndpointBehaviours {
  me: BaseISpecIt =>
  type PlayRequest = Request[AnyContent] => Future[Result]

  protected def authenticatedAs(user: SampleUser, method: String = GET): FakeRequest[AnyContentAsEmpty.type]

  protected def anAgentAffinityGroupOnlyEndpoint(doRequest: PlayRequest): Unit = {
    "redirect to the company-auth-frontend sign-in page if the current user is not logged in" in new TestSetupNoJourneyRecord {
      AuthStub.userIsNotAuthenticated()

      val request = FakeRequest()
      val result = doRequest(request)

      status(result) shouldBe 303
      redirectLocation(result.futureValue).get should include("/bas-gateway/sign-in")
      noMetricExpectedAtThisPoint()
    }

    "redirect to the non-Agent next steps page if the current user is logged in and does not have affinity group = Agent" in new TestSetupNoJourneyRecord {
      val sessionKeys = AuthStub.userIsNotAnAgent(individual)

      val request = FakeRequest().withSession(sessionKeys: _*)
      val result = doRequest(request)

      status(result) shouldBe 303
      redirectLocation(result.futureValue).get shouldBe routes.StartController.showNotAgent().url
      metricShouldExistAndBeUpdated("Count-Subscription-NonAgent")
    }
  }

  protected def aPageWithFeedbackLinks(
    action: PlayRequest,
    request: => Request[AnyContent] = FakeRequest("GET", "url").withSession(SessionKeys.authToken -> "Bearer XYZ")
  ): Unit =
    "have a 'is this page not working properly?' link" in new TestSetupWithCompleteJourneyRecord {
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(BusinessType.SoleTrader)))(request, global, aesCrypto))
      val result = action(request)

      bodyOf(result.futureValue) should include("Is this page not working properly?")
    }

  private def urlencoded(toEncode: String) = URLEncoder.encode(toEncode, "UTF-8")

  private def aPageTakingContinueUrl(
    action: PlayRequest,
    sessionKeys: => Seq[(String, String)],
    assertContinueUrlKept: (Request[AnyContent], Result, String) => Assertion,
    assertContinueUrlNotKept: (Request[AnyContent], Result, Option[String]) => Assertion
  ): Unit = {
    def doRequestWithContinueUrl(continueUrl: String) = {
      implicit val request = FakeRequest("GET", s"?continue=${urlencoded(continueUrl)}").withSession(sessionKeys: _*)
      await(sessionStoreService.cacheAgentSession(AgentSession(businessType = Some(BusinessType.SoleTrader)))(request, global, aesCrypto))
      val result = action(request)
      (request, result)
    }

    "include absolute continue URL" in new TestSetupNoJourneyRecord {
      SsoStub.givenAllowlistedDomainsExist
      val continueUrl = "http://localhost"
      val (request, result) = doRequestWithContinueUrl(continueUrl)
      assertContinueUrlKept(request, result.futureValue, continueUrl)
    }

    "include relative continue URL" in new TestSetupNoJourneyRecord {
      val continueUrl = "/foo"
      val (request, result) = doRequestWithContinueUrl(continueUrl)
      assertContinueUrlKept(request, result.futureValue, continueUrl)
    }

    "include continue URL if it's the absolute www.tax.service.gov.uk continue url" in new TestSetupNoJourneyRecord {
      val continueUrl = "http://www.tax.service.gov.uk/foo/bar?some=true"
      val (request, result) = doRequestWithContinueUrl(continueUrl)
      assertContinueUrlKept(request, result.futureValue, continueUrl)
    }

    "include continue URL if it's allowlisted" in new TestSetupNoJourneyRecord {
      val continueUrl = "http://online-qa.ibt.hmrc.gov.uk/foo?some=false"
      val (request, result) = doRequestWithContinueUrl(continueUrl)
      assertContinueUrlKept(request, result.futureValue, continueUrl)
    }

    "not include a continue URL if it's not allowlisted" in new TestSetupNoJourneyRecord {
      val continueUrl = "http://www.foo.org/bar?some=false"
      val (request, result) = doRequestWithContinueUrl(continueUrl)
      assertContinueUrlNotKept(request, result.futureValue, Some(continueUrl))
    }

    "not include a continue URL if it contains an invalid character" in new TestSetupNoJourneyRecord {
      val continueUrl = "http://www@foo.com"
      val (request, result) = doRequestWithContinueUrl(continueUrl)
      assertContinueUrlNotKept(request, result.futureValue, Some(continueUrl))
    }

    "not include a continue URL if it's not provided" in new TestSetupNoJourneyRecord {
      implicit val request = FakeRequest("GET", "/").withSession(sessionKeys: _*)
      await(sessionStoreService.cacheAgentSession(AgentSession(businessType = Some(BusinessType.SoleTrader)))(request, global, aesCrypto))
      val result = action(request)
      assertContinueUrlNotKept(request, result.futureValue, None)
    }
  }

  protected def anEndpointTakingContinueUrlAndRedirectingWithIt(action: PlayRequest): Unit = {
    aPageTakingContinueUrl(action, Seq(), checkRedirectHasContinueUrl, checkRedirectHasNoContinueUrl)

    def continueParamRegex(expectedContinueUrl: String) = s"[\\?&]continue=${urlencoded(expectedContinueUrl)}".r

    def checkRedirectHasContinueUrl(request: Request[AnyContent], result: Result, expectedContinueUrl: String) = {
      status(result) shouldBe 303
      result.header.headers(LOCATION) should include regex continueParamRegex(expectedContinueUrl)
    }

    def checkRedirectHasNoContinueUrl(request: Request[AnyContent], result: Result, expectedContinueUrl: Option[String]) = {
      status(result) shouldBe 303
      result.header.headers(LOCATION) should not include "continue="
    }
  }

  protected def aPageTakingContinueUrlAndContainingItAsALink(action: PlayRequest): Unit = {
    aPageTakingContinueUrl(action, Seq(), checkResultHasLinkWithContinueUrl, checkResultHasOnlyLinksWithoutContinueUrl)

    def checkResultHasLinkWithContinueUrl(request: Request[AnyContent], result: Result, expectedContinueUrl: String) = {
      status(result) shouldBe 200
      bodyOf(result) should include regex (s""".*<a .*href=.*\\?continue=${urlencoded(expectedContinueUrl)}.*</a>""".r)
    }

    def checkResultHasOnlyLinksWithoutContinueUrl(request: Request[AnyContent], result: Result, expectedContinueUrl: Option[String]) = {
      status(result) shouldBe 200
      bodyOf(result) should include regex (s""".*<a .*href=.*\\.*</a>""".r)
      val encodedUrl = urlencoded(expectedContinueUrl.getOrElse(""))
      bodyOf(result) should not include regex(s""".*<a .*href=.*\\?continue=$encodedUrl.*</a>""".r)
    }
  }

  protected def aPageTakingContinueUrlAndCachingInSessionStore(
    action: PlayRequest,
    sessionKeys: => Seq[(String, String)],
    expectedStatusCode: Int = 200
  ): Unit = {
    aPageTakingContinueUrl(action, sessionKeys, checkContinueUrlIsInCache, checkContinueUrlIsNotInCache)

    def checkContinueUrlIsInCache(request: Request[AnyContent], result: Result, expectedContinueUrl: String) = {
      status(result) shouldBe expectedStatusCode
      withClue("ContinueUrl was not found in session store, actual: ") {
        sessionStoreService.currentSession(request).continueUrl shouldBe Some(expectedContinueUrl)
      }
    }

    def checkContinueUrlIsNotInCache(request: Request[AnyContent], result: Result, expectedContinueUrl: Option[String]) = {
      status(result) shouldBe expectedStatusCode
      withClue("A ContinueUrl was found in session store, it was: ") {
        sessionStoreService.currentSession(request).continueUrl shouldBe None
      }
    }
  }
}
