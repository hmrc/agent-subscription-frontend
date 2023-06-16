package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.SsoStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, MetricTestSupport}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global

class SsoConnectorISpecIt extends BaseISpecIt with MetricTestSupport {

  private lazy val connector = new SsoConnector(
    app.injector.instanceOf[HttpClient],
    app.injector.instanceOf[Metrics],
    app.injector.instanceOf[AppConfig])
  private implicit val hc = HeaderCarrier()

  "SsoConnector" should {
    "return valid domains" in {
      withMetricsTimerUpdate("ConsumedAPI-SSO-getExternalDomains-GET") {
        SsoStub.givenAllowlistedDomainsExist
        val result = await(connector.getAllowlistedDomains())
        result shouldBe Set("online-qa.ibt.hmrc.gov.uk", "localhost", "ibt.hmrc.gov.uk", "127.0.0.1", "www.tax.service.gov.uk")
      }
    }

    "return an empty set when the service throws an error" in {
      withMetricsTimerUpdate("ConsumedAPI-SSO-getExternalDomains-GET") {
        SsoStub.givenAllowlistedDomainsError
        val result = await(connector.getAllowlistedDomains())
        result shouldBe Set.empty
      }
    }
  }
}