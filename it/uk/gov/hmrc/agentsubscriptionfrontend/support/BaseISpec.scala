package uk.gov.hmrc.agentsubscriptionfrontend.support

import com.google.inject.AbstractModule
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import play.twirl.api.HtmlFormat.escape
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.SsoConnector
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub.userIsAuthenticated
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.DataStreamStubs
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

abstract class BaseISpec extends UnitSpec with OneAppPerSuite with MongoApp with WireMockSupport with EndpointBehaviours with DataStreamStubs {

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder = {
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "microservice.services.agent-subscription.port" -> wireMockPort,
        "microservice.services.address-lookup-frontend.port" -> wireMockPort,
        "microservice.services.sso.port" -> wireMockPort,
        "passcodeAuthentication.enabled" -> passcodeAuthenticationEnabled,
        "microservice.services.agent-assurance.port" -> wireMockPort,
        "auditing.enabled" -> true,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort
      )
      .configure(mongoConfiguration)
      .overrides(new TestGuiceModule)
  }

  override def commonStubs(): Unit = {
    givenAuditConnector()
  }

  protected def passcodeAuthenticationEnabled: Boolean = false

  protected lazy val sessionStoreService = new TestSessionStoreService

  private class TestGuiceModule extends AbstractModule {
    override def configure(): Unit = {
      bind(classOf[SessionStoreService]).toInstance(sessionStoreService)
      bind(classOf[SsoConnector]).toInstance(new SsoConnector(null, null) {
        val whitelistedSSODomains = Set("www.foo.com", "foo.org")

        override def validateExternalDomain(domain: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
          Future.successful(whitelistedSSODomains.contains(domain))
        }
      })
    }
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    sessionStoreService.clear()
  }

  protected implicit val materializer = app.materializer

  protected def authenticatedRequest(user: SampleUser = subscribingAgent): FakeRequest[AnyContentAsEmpty.type] = {
    val sessionKeys = userIsAuthenticated(user)
    FakeRequest().withSession(sessionKeys: _*)
  }


  protected def checkHtmlResultWithBodyText(result: Result, expectedSubstrings: String*): Unit = {
    status(result) shouldBe OK
    contentType(result) shouldBe Some("text/html")
    charset(result) shouldBe Some("utf-8")
    expectedSubstrings.foreach(s => bodyOf(result) should include(s))
  }

  private val messagesApi = app.injector.instanceOf[MessagesApi]
  private implicit val messages: Messages = messagesApi.preferred(Seq.empty[Lang])

  protected def htmlEscapedMessage(key: String, args: Any*): String = escape(Messages(key, args: _*)).toString

  implicit def hc(implicit request: FakeRequest[_]): HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

}


