/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.auth

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.SEE_OTHER
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Request, Result, Results}
import play.api.test.FakeRequest
import play.api.{Application, Configuration}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.RedirectUrlActions
import uk.gov.hmrc.agentsubscriptionfrontend.service.SubscriptionJourneyService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthActionWithRedirectISpec extends AnyWordSpecLike with Matchers with ScalaFutures with GuiceOneAppPerSuite {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "features.enable-redirect-to-agent-registration" -> true
      )

  override implicit lazy val app: Application = appBuilder.build()

  implicit val request: Request[_] = FakeRequest()

  val testAuthAction = new AuthActions {

    val config = app.injector.instanceOf[Configuration]
    override def authConnector: AuthConnector = app.injector.instanceOf[AuthConnector]
    override def appConfig: AppConfig = app.injector.instanceOf[AppConfig]
    override def redirectUrlActions: RedirectUrlActions = app.injector.instanceOf[RedirectUrlActions]
    override def metrics: Metrics = app.injector.instanceOf[Metrics]
    override def subscriptionJourneyService: SubscriptionJourneyService = app.injector.instanceOf[SubscriptionJourneyService]
  }

  "with redirect feature flag enabled subscribingAgent authAction" should {
    "redirect the request to /agent-registration/apply" in {

      val result = testAuthAction.withSubscribingAgent((_: Agent) => Future.successful(Results.Ok)).futureValue

      result.header.status shouldBe SEE_OTHER
      result.header.headers.get("location").get shouldBe "http://localhost:22201/agent-registration/apply"
    }
  }
}
