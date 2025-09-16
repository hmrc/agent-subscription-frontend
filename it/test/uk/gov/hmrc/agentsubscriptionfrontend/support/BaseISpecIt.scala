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

import com.google.inject.AbstractModule
import org.jsoup.Jsoup
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{Assertion, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsValue
import play.api.mvc.{AnyContentAsEmpty, Request, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.twirl.api.HtmlFormat.escape
import uk.gov.hmrc.agentsubscriptionfrontend.service.MongoDBSessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub.userIsAuthenticated
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.DataStreamStubs
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, SymmetricCryptoFactory}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.Future

abstract class BaseISpecIt
    extends AnyWordSpecLike with Matchers with OptionValues with ScalaFutures with GuiceOneAppPerSuite with WireMockSupport with EndpointBehaviours
    with DataStreamStubs {

  // Note: This is simply a randomly-chosen secret key to run tests
  val aesCrypto: Encrypter with Decrypter =
    SymmetricCryptoFactory.aesCrypto(secretKey = "hWmZq3t6w9zrCeF5JiNcRfUjXn2r5u7x")
  def status(result: Result): Int = result.header.status
  def status(result: Future[Result]): Int = Helpers.status(result)
  def bodyOf(result: Result): String = Helpers.contentAsString(Future.successful(result))
  def redirectLocation(result: Result): Option[String] = Helpers.redirectLocation(Future.successful(result))
  def contentAsString(result: Result): String = Helpers.contentAsString(Future.successful(result))
  def contentAsJson(result: Result): JsValue = Helpers.contentAsJson(Future.successful(result))
  def contentType(result: Result): Option[String] =
    result.body.contentType.map(_.split(";").take(1).mkString.trim)

  def charset(result: Result): Option[String] =
    result.body.contentType match {
      case Some(s) if s.contains("charset=") => Some(s.split("; *charset=").drop(1).mkString.trim)
      case _                                 => None
    }
  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port"                                     -> wireMockPort,
        "microservice.services.agent-subscription.port"                       -> wireMockPort,
        "microservice.services.agent-subscription-frontend.external-url"      -> "",
        "microservice.services.address-lookup-frontend.port"                  -> wireMockPort,
        "microservice.services.sso.port"                                      -> wireMockPort,
        "microservice.services.agent-assurance.port"                          -> wireMockPort,
        "microservice.services.agent-mapping.port"                            -> wireMockPort,
        "microservice.services.government-gateway-registration-frontend.host" -> wireMockHost,
        "microservice.services.government-gateway-registration-frontend.port" -> wireMockPort,
        "auditing.enabled"                                                    -> true,
        "auditing.consumer.baseUri.host"                                      -> wireMockHost,
        "auditing.consumer.baseUri.port"                                      -> wireMockPort,
        "features.enable-welsh-toggle"                                        -> true,
        "login.continue"                                                      -> "",
        "bas-gateway.url"                                                     -> "/bas-gateway/sign-in"
      )
      .overrides(new TestGuiceModule)

  override def commonStubs(): Unit =
    givenAuditConnector()

  protected lazy val sessionStoreService = new TestSessionStoreService

  private class TestGuiceModule extends AbstractModule {
    override def configure(): Unit =
      bind(classOf[MongoDBSessionStoreService]).toInstance(sessionStoreService)
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    sessionStoreService.clear()
  }

  protected implicit val materializer = app.materializer

  protected def authenticatedAs(user: SampleUser, method: String = GET): FakeRequest[AnyContentAsEmpty.type] = {
    val sessionKeys = userIsAuthenticated(user)
    FakeRequest(method, "/").withSession(sessionKeys: _*)
  }

  protected def checkMessageIsDefined(messageKey: String): Assertion =
    withClue(s"Message key ($messageKey) should be defined: ") {
      Messages.isDefinedAt(messageKey) shouldBe true
    }

  protected def checkIsHtml200(result: Result): Assertion = {
    status(result) shouldBe OK
    charset(result) shouldBe Some("utf-8")
    contentType(result) shouldBe Some("text/html")
  }

  protected def checkHtmlResultWithBodyText(result: Result, expectedSubstrings: String*): Unit = {
    contentType(result) shouldBe Some("text/html")
    charset(result) shouldBe Some("utf-8")
    expectedSubstrings.foreach(s => bodyOf(result) should include(s))
  }

  protected def checkHtmlResultWithNotBodyText(result: Result, expectedSubstrings: String*): Unit = {
    contentType(result) shouldBe Some("text/html")
    charset(result) shouldBe Some("utf-8")
    expectedSubstrings.foreach(s => bodyOf(result) should not include s)
  }

  protected def containSubstrings(expectedSubstrings: String*): Matcher[Result] =
    new Matcher[Result] {
      override def apply(result: Result): MatchResult = {
        checkIsHtml200(result)

        val resultBody = bodyOf(result)
        val (strsPresent, strsMissing) = expectedSubstrings.partition { expectedSubstring =>
          expectedSubstring.trim should not be ""
          resultBody.contains(expectedSubstring)
        }

        MatchResult(
          strsMissing.isEmpty,
          s"Expected substrings are missing in the response: ${strsMissing.mkString("\"", "\", \"", "\"")}",
          s"Expected substrings are present in the response : ${strsPresent.mkString("\"", "\", \"", "\"")}"
        )
      }
    }

  protected def containMessages(expectedMessageKeys: String*): Matcher[Result] =
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

  protected def containNoMessages(forbiddenKeys: String*): Matcher[Result] =
    new Matcher[Result] {
      override def apply(result: Result): MatchResult = {
        forbiddenKeys.foreach(checkMessageIsDefined)
        checkIsHtml200(result)

        val resultBody = bodyOf(result)
        val present = forbiddenKeys.filter(k => resultBody.contains(htmlEscapedMessage(k)))

        MatchResult(
          present.isEmpty,
          s"Content unexpectedly contained message keys: ${present.mkString(", ")}",
          s"Content contains none of the forbidden message keys."
        )
      }
    }

  protected def containInputElement(expectedElementId: String, expectedInputType: String, expectedValue: Option[String] = None): Matcher[Result] =
    new Matcher[Result] {
      override def apply(result: Result): MatchResult = {
        val doc = Jsoup.parse(bodyOf(result))

        val foundElement = doc.getElementById(expectedElementId)

        val isAsExpected = Option(foundElement) match {
          case None => false
          case Some(el) =>
            val isExpectedTag = el.tagName() == "input"
            val isExpectedType = el.attr("type") == expectedInputType
            val isExpectedValue = expectedValue.fold(true)(el.attr("value") == _)
            isExpectedTag && isExpectedType && isExpectedValue
        }

        MatchResult(
          isAsExpected,
          s"""Response does not contain an input element of type "$expectedInputType" with id "$expectedElementId"""",
          s"""Response contains an input element of type "$expectedInputType" with id "$expectedElementId""""
        )
      }
    }

  protected def containSubmitButton(expectedMessageKey: String, expectedElementId: String, expectedTagName: String = "button"): Matcher[Result] =
    new Matcher[Result] {
      override def apply(result: Result): MatchResult = {
        val doc = Jsoup.parse(bodyOf(result))

        checkMessageIsDefined(expectedMessageKey)

        val foundElement = doc.getElementById(expectedElementId)

        val isAsExpected = Option(foundElement) match {
          case None => false
          case Some(elAmls) =>
            val isExpectedTag = elAmls.tagName() == expectedTagName
            val hasExpectedMsg = elAmls.text() == htmlEscapedMessage(expectedMessageKey)
            isExpectedTag && hasExpectedMsg
        }

        MatchResult(
          isAsExpected,
          s"""Response does not contain a submit button with id "$expectedElementId" with content for message key "$expectedMessageKey" """,
          s"""Response contains a submit button with id "$expectedElementId" with content for message key "$expectedMessageKey" """
        )
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

  protected def containLink(expectedMessageKey: String, expectedHref: String): Matcher[Result] = {
    import scala.jdk.CollectionConverters._
    new Matcher[Result] {
      override def apply(result: Result): MatchResult = {
        val doc = Jsoup.parse(bodyOf(result))
        checkMessageIsDefined(expectedMessageKey)
        val foundElements = doc.select(s"a[href=$expectedHref]")

        val wasFoundWithCorrectMessage = foundElements.asScala.toList.exists(_.text() == htmlEscapedMessage(expectedMessageKey))
        MatchResult(
          wasFoundWithCorrectMessage,
          s"""Response does not contain a link to "$expectedHref" with content for message key "$expectedMessageKey" """,
          s"""Response contains a link to "$expectedHref" with content for message key "$expectedMessageKey" """
        )
      }
    }
  }

  protected def containLinkText(expectedMessageText: String, expectedHref: String): Matcher[Result] = {
    import scala.jdk.CollectionConverters._
    new Matcher[Result] {
      override def apply(result: Result): MatchResult = {
        val doc = Jsoup.parse(bodyOf(result))
        val foundElements = doc.select(s"a[href=$expectedHref]")

        val wasFoundWithCorrectMessage = foundElements.asScala.toList.exists(_.text() == expectedMessageText)
        MatchResult(
          wasFoundWithCorrectMessage,
          s"""Response does not contain a link to "$expectedHref" with link text "$expectedMessageText" """,
          s"""Response contains a link to "$expectedHref" with link text "$expectedMessageText" """
        )
      }
    }
  }

  private val messagesApi = app.injector.instanceOf[MessagesApi]
  private implicit val messages: Messages = messagesApi.preferred(Seq.empty[Lang])

  protected def htmlEscapedMessage(key: String, args: Any*): String = escape(Messages(key, args: _*)).toString

  implicit def hc(implicit request: Request[_]): HeaderCarrier =
    HeaderCarrierConverter.fromRequestAndSession(request, request.session)

  protected def hasMessage(key: String, args: Any*): String = Messages(key, args: _*).toString

}
