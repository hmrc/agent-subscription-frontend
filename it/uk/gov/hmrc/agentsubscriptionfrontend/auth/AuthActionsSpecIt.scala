package uk.gov.hmrc.agentsubscriptionfrontend.auth

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Results._
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.RedirectUrlActions
import uk.gov.hmrc.agentsubscriptionfrontend.models.AuthProviderId
import uk.gov.hmrc.agentsubscriptionfrontend.service.SubscriptionJourneyService
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.givenSubscriptionJourneyRecordExists
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.minimalSubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestSetupNoJourneyRecord}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, SessionKeys}

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

    def withSubscribedAgent[A]: Result =
      await(super.withSubscribedAgent { (arn, sjr) => Future.successful(Ok(arn.value)) })

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
}
