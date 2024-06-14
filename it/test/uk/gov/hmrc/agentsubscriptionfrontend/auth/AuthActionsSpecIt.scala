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

package uk.gov.hmrc.agentsubscriptionfrontend.auth

import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Results._
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.RedirectUrlActions
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AuthProviderId, ContactEmailData}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SubscriptionJourneyService
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.givenSubscriptionJourneyRecordExists
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{completeJourneyRecordNoMappings, completeJourneyRecordWithMappingsNoVerifiedEmails, minimalSubscriptionJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestSetupNoJourneyRecord}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, SessionKeys}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.Future

class AuthActionsSpecIt extends BaseISpecIt with MockitoSugar {

  val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  object TestController extends AuthActions {

    implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization("Bearer XYZ")))
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(SessionKeys.authToken -> "Bearer XYZ")
    import scala.concurrent.ExecutionContext.Implicits.global
    val env = app.injector.instanceOf[Environment]
    val config = app.injector.instanceOf[Configuration]

    override def authConnector: AuthConnector = app.injector.instanceOf[AuthConnector]
    override def appConfig: AppConfig = app.injector.instanceOf[AppConfig]
    override def redirectUrlActions: RedirectUrlActions = app.injector.instanceOf[RedirectUrlActions]
    override def metrics: Metrics = app.injector.instanceOf[Metrics]
    override def subscriptionJourneyService: SubscriptionJourneyService = app.injector.instanceOf[SubscriptionJourneyService]
    val agent = new Agent(Set(Enrolment("", Seq.empty[EnrolmentIdentifier], "", None)), Some(Credentials("test-provider-id", "test")), None, None)
    val body: Agent => Future[Result] = _ => Future.successful(Ok("subscribing agent with email verification"))

    def withSubscribedAgent[A]: Result =
      await(super.withSubscribedAgent((arn, sjr) => Future.successful(Ok(arn.value))))

    def withSubscribedAgentCheckEmail[A]: Result =
      await(super.withSubscribingEmailVerifiedAgent(body))
  }

  "withSubscribedAgent" should {
    val providerId = AuthProviderId("12345-credId")

    "call body with arn when valid agent" in new TestSetupNoJourneyRecord {
      givenSubscriptionJourneyRecordExists(providerId, minimalSubscriptionJourneyRecord(providerId))
      authenticatedAgent("fooArn", "12345-credId")

      val result = TestController.withSubscribedAgent

      status(result) shouldBe 200
      bodyOf(result) shouldBe "fooArn"
    }

    "throw Forbidden when agent not enrolled for service" in new TestSetupNoJourneyRecord {
      givenSubscriptionJourneyRecordExists(providerId, minimalSubscriptionJourneyRecord(providerId))
      userHasInsufficientEnrolments()
      status(TestController.withSubscribedAgent) shouldBe 403
    }

    "throw Forbidden when expected agent's identifier missing" in new TestSetupNoJourneyRecord {
      givenSubscriptionJourneyRecordExists(providerId, minimalSubscriptionJourneyRecord(providerId))
      givenAuthorisedFor(
        "{}",
        s"""{
           |"authorisedEnrolments": [
           |  { "key":"HMRC-AS-AGENT", "identifiers": [
           |    { "key":"BAR", "value": "fooArn" }
           |  ]}
           |],
           |"optionalCredentials": {"providerId": "${providerId.id}", "providerType": "GovernmentGateway"}}""".stripMargin
      )

      status(TestController.withSubscribedAgent) shouldBe 403
    }

    "UnsupportedAuthProvider error should redirect user to start page" in new TestSetupNoJourneyRecord {
      userLoggedInViaUnsupportedAuthProvider()
      val result = TestController.withSubscribedAgent

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/agent-subscription/finish-sign-out")
    }
  }
  "withSubscribedAgent Email check" should {
    val providerId = AuthProviderId("12345-credId")

    " check if the email needs verification and let it pass if it does not" in new TestSetupNoJourneyRecord {
      givenSubscriptionJourneyRecordExists(providerId, completeJourneyRecordNoMappings)
      authenticatedAgentEmailCheck("fooArn", "12345-credId")

      val result = TestController.withSubscribedAgentCheckEmail

      status(result) shouldBe 200
      bodyOf(result) shouldBe "subscribing agent with email verification"
    }
    " check if the email needs verification and and redirect if it does " in new TestSetupNoJourneyRecord {

      givenSubscriptionJourneyRecordExists(providerId, completeJourneyRecordWithMappingsNoVerifiedEmails)
      authenticatedAgentEmailCheck("fooArn", "12345-credId")

      val result = TestController.withSubscribedAgentCheckEmail

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/agent-subscription/verify-email")
    }
    " check if the email is the same as one supplied in auth and dont redirect if it does " in new TestSetupNoJourneyRecord {

      givenSubscriptionJourneyRecordExists(providerId, completeJourneyRecordWithMappingsNoVerifiedEmails)
      authenticatedAgentEmailCheck("fooArn", "12345-credId", Some("email@email.com"))

      val result = TestController.withSubscribedAgentCheckEmail

      status(result) shouldBe 200
      bodyOf(result) shouldBe "subscribing agent with email verification"
    }
    " check if the email is the same as one supplied in auth and dont redirect if it does (ignore case)" in new TestSetupNoJourneyRecord {

      givenSubscriptionJourneyRecordExists(
        providerId,
        completeJourneyRecordWithMappingsNoVerifiedEmails.copy(contactEmailData = Some(ContactEmailData(false, Some("eMaiL@email.Com"))))
      )
      authenticatedAgentEmailCheck("fooArn", "12345-credId", Some("EmAil@email.com"))

      val result = TestController.withSubscribedAgentCheckEmail

      status(result) shouldBe 200
      bodyOf(result) shouldBe "subscribing agent with email verification"
    }
    " check if the email is the same as one supplied in auth and  redirect if it doesnt" in new TestSetupNoJourneyRecord {
      givenSubscriptionJourneyRecordExists(providerId, completeJourneyRecordWithMappingsNoVerifiedEmails)
      authenticatedAgentEmailCheck("fooArn", "12345-credId", Some("emailNotmatching@email.com"))

      val result = TestController.withSubscribedAgentCheckEmail

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/agent-subscription/verify-email")
    }

  }
}
