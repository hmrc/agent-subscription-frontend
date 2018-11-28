package uk.gov.hmrc.agentsubscriptionfrontend.support

import com.codahale.metrics.MetricRegistry
import com.google.inject.AbstractModule
import com.kenshoo.play.metrics.Metrics
import org.jsoup.Jsoup
import org.scalatest.matchers.{MatchResult, Matcher}
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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

abstract class BaseISpec
    extends UnitSpec with OneAppPerSuite with MongoApp with WireMockSupport with EndpointBehaviours with DataStreamStubs
    with MetricTestSupport {

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port"                    -> wireMockPort,
        "microservice.services.agent-subscription.port"      -> wireMockPort,
        "microservice.services.address-lookup-frontend.port" -> wireMockPort,
        "microservice.services.sso.port"                     -> wireMockPort,
        "microservice.services.agent-assurance.port"         -> wireMockPort,
        "microservice.services.agent-mapping.port"         -> wireMockPort,
        "auditing.enabled"                                   -> true,
        "auditing.consumer.baseUri.host"                     -> wireMockHost,
        "auditing.consumer.baseUri.port"                     -> wireMockPort
      )
      .configure(mongoConfiguration)
      .overrides(new TestGuiceModule)

  override def commonStubs(): Unit = {
    givenAuditConnector()
    givenCleanMetricRegistry()
  }

  protected lazy val sessionStoreService = new TestSessionStoreService

  private object FakeMetrics extends Metrics {
    override def defaultRegistry: MetricRegistry = new MetricRegistry

    override def toJson: String = ???
  }

  protected lazy val testSsoConnector = new SsoConnector(null, null, FakeMetrics) {
    val whitelistedSSODomains = Set("www.foo.com", "foo.org")

    override def validateExternalDomain(domain: String)(implicit hc: HeaderCarrier): Future[Boolean] =
      Future.successful(whitelistedSSODomains.contains(domain))
  }

  private class TestGuiceModule extends AbstractModule {
    override def configure(): Unit = {
      bind(classOf[SessionStoreService]).toInstance(sessionStoreService)
      bind(classOf[SsoConnector]).toInstance(testSsoConnector)
    }
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    sessionStoreService.clear()
  }

  protected implicit val materializer = app.materializer

  protected def authenticatedAs(user: SampleUser): FakeRequest[AnyContentAsEmpty.type] = {
    val sessionKeys = userIsAuthenticated(user)
    FakeRequest().withSession(sessionKeys: _*)
  }

  protected def checkMessageIsDefined(messageKey: String) = {
    withClue(s"Message key ($messageKey) should be defined: ") {
      Messages.isDefinedAt(messageKey) shouldBe true
    }
  }

  protected def checkIsHtml200(result: Result) = {
    status(result) shouldBe OK
    charset(result) shouldBe Some("utf-8")
    contentType(result) shouldBe Some("text/html")
  }

  protected def containSubstrings(expectedSubstrings: String*): Matcher[Result] = {
    new Matcher[Result] {
      override def apply(result: Result): MatchResult = {
        checkIsHtml200(result)

        val resultBody = bodyOf(result)
        val (strsPresent, strsMissing) = expectedSubstrings.partition{ expectedSubstring =>
          expectedSubstring.trim should not be ""
          resultBody.contains(expectedSubstring)
        }

        MatchResult(strsMissing.isEmpty,
          s"Expected substrings are missing in the response: ${strsMissing.mkString("\"", "\", \"", "\"")}",
          s"Expected substrings are present in the response : ${strsPresent.mkString("\"", "\", \"", "\"")}")
      }
    }
  }

  protected def containMessages(expectedMessageKeys: String*): Matcher[Result] = {
    new Matcher[Result] {
      override def apply(result: Result): MatchResult = {
        expectedMessageKeys.foreach(checkMessageIsDefined)
        checkIsHtml200(result)

        val resultBody = bodyOf(result)
        val (msgsPresent, msgsMissing) = expectedMessageKeys.partition { messageKey =>
          resultBody.contains(htmlEscapedMessage(messageKey))
        }

        MatchResult(
          msgsMissing.isEmpty,
          s"Content is missing in the response for message keys: ${msgsMissing.mkString(", ")}",
          s"Content is present in the response for message keys: ${msgsPresent.mkString(", ")}"
        )
      }
    }
  }

  protected def repeatMessage(expectedMessageKey: String, times: Int): Matcher[Result] = new Matcher[Result] {
    override def apply(result: Result): MatchResult = {
      checkIsHtml200(result)

      MatchResult(
        Messages(expectedMessageKey).r.findAllMatchIn(bodyOf(result)).size == times,
        s"The message keys $expectedMessageKey does not appear $times times in the content",
        s"The message keys $expectedMessageKey appears $times times in the content"
      )
    }
  }

  protected def withMetricsTimerUpdate(expectedMetricName: String)(testCode: => Unit): Unit = {
    givenCleanMetricRegistry()
    testCode
    timerShouldExistAndBeUpdated(expectedMetricName)
  }

  private val messagesApi = app.injector.instanceOf[MessagesApi]
  private implicit val messages: Messages = messagesApi.preferred(Seq.empty[Lang])

  protected def htmlEscapedMessage(key: String, args: Any*): String = escape(Messages(key, args: _*)).toString

  implicit def hc(implicit request: FakeRequest[_]): HeaderCarrier =
    HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

protected def hasMessage(key: String, args: Any*): String = Messages(key, args: _*).toString

}
