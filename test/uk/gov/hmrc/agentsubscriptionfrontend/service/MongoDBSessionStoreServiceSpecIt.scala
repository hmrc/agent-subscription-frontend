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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import org.scalamock.scalatest.MockFactory
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.models.AgentSession
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.support.UnitSpec
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class MongoDBSessionStoreServiceSpecIt extends UnitSpec with MockFactory {

  val utr = "2000000000"
  val testPostcode = "AA1 1AA"

  val mockSessionStoreService: MongoDBSessionStoreService = mock[MongoDBSessionStoreService]
  implicit lazy val req: Request[_] = FakeRequest()
  implicit val crypto: Encrypter with Decrypter = aesCrypto

  "cacheContinueUrl and fetchContinueUrl" should {
    "cache a continue url and fetch it back" in {
      (mockSessionStoreService
        .cacheContinueUrl(_: RedirectUrl)(_: Request[_], _: ExecutionContext))
        .expects(RedirectUrl("/continue/url"), req, *)
        .returns(Future(()))
      (mockSessionStoreService
        .fetchContinueUrl(_: Request[_], _: ExecutionContext))
        .expects(req, *)
        .returns(Future(Some(RedirectUrl("/continue/url"))))

      mockSessionStoreService.cacheContinueUrl(RedirectUrl("/continue/url"))
      val result = await(mockSessionStoreService.fetchContinueUrl)
      result shouldBe Some(RedirectUrl("/continue/url"))
    }
  }

  "cacheGoBackUrl and fetchGoBackUrl" should {
    "cache a go back url and fetch it back" in {
      (mockSessionStoreService
        .cacheGoBackUrl(_: String)(_: Request[_], _: ExecutionContext))
        .expects("/go/back", req, *)
        .returns(Future(()))
      (mockSessionStoreService
        .fetchGoBackUrl(_: Request[_], _: ExecutionContext))
        .expects(req, *)
        .returns(Future(Some("/go/back")))

      mockSessionStoreService.cacheGoBackUrl("/go/back")
      val result = await(mockSessionStoreService.fetchGoBackUrl)
      result shouldBe Some("/go/back")
    }
  }

  "cacheIsChangingAnswers and fetchIsChangingAnswers" should {
    "cache whether a user is changing answers and fetch it back" in {
      (mockSessionStoreService
        .cacheIsChangingAnswers(_: Boolean)(_: Request[_], _: ExecutionContext))
        .expects(true, req, *)
        .returns(Future(()))
      (mockSessionStoreService
        .fetchIsChangingAnswers(_: Request[_], _: ExecutionContext))
        .expects(req, *)
        .returns(Future(Some(true)))

      mockSessionStoreService.cacheIsChangingAnswers(changing = true)
      val result = await(mockSessionStoreService.fetchIsChangingAnswers)
      result shouldBe Some(true)
    }
  }

  "cacheAgentSession and fetchAgentSession" should {
    val agentSession =
      AgentSession(businessType = Some(SoleTrader), utr = Some(utr), postcode = Some(testPostcode))
    "cache an agent session and fetch it back" in {
      (mockSessionStoreService
        .cacheAgentSession(_: AgentSession)(_: Request[_], _: ExecutionContext, _: Encrypter with Decrypter))
        .expects(agentSession, req, *, aesCrypto)
        .returns(Future(()))
      (mockSessionStoreService
        .fetchAgentSession(_: Request[_], _: ExecutionContext, _: Encrypter with Decrypter))
        .expects(req, *, aesCrypto)
        .returns(Future(Some(agentSession)))

      mockSessionStoreService.cacheAgentSession(agentSession)
      val result = await(mockSessionStoreService.fetchAgentSession)
      result shouldBe Some(agentSession)
    }
  }

  "remove" should {
    "clear the session" in {
      (mockSessionStoreService
        .cacheContinueUrl(_: RedirectUrl)(_: Request[_], _: ExecutionContext))
        .expects(RedirectUrl("/continue/url"), req, *)
        .returns(Future(()))
      (mockSessionStoreService
        .fetchContinueUrl(_: Request[_], _: ExecutionContext))
        .expects(req, *)
        .returns(Future(Some(RedirectUrl("/continue/url"))))
      (mockSessionStoreService
        .remove()(_: Request[_], _: ExecutionContext))
        .expects(*, *)
        .returns(Future(()))
      (mockSessionStoreService
        .fetchContinueUrl(_: Request[_], _: ExecutionContext))
        .expects(req, *)
        .returns(Future(None))

      mockSessionStoreService.cacheContinueUrl(RedirectUrl("/continue/url"))
      val fetchResult = await(mockSessionStoreService.fetchContinueUrl)
      fetchResult shouldBe Some(RedirectUrl("/continue/url"))

      mockSessionStoreService.remove()
      val fetchResultEmpty = await(mockSessionStoreService.fetchContinueUrl)
      fetchResultEmpty shouldBe None
    }
  }

}
