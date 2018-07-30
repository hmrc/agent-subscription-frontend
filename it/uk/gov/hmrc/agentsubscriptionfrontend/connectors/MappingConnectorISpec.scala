package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.MappingStubs
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, MetricTestSupport}
import uk.gov.hmrc.http._

class MappingConnectorISpec extends BaseISpec with MetricTestSupport {

  private implicit val hc = HeaderCarrier()

  private lazy val connector: MappingConnector =
    new MappingConnector(
      new URL(s"http://localhost:$wireMockPort"),
      app.injector.instanceOf[HttpGet with HttpPost with HttpPut],
      app.injector.instanceOf[Metrics])

  private def withMetricsTimerUpdate(expectedMetricName: String)(testCode: => Unit): Unit = {
    givenCleanMetricRegistry()
    testCode
    timerShouldExistAndBeUpdated(expectedMetricName)
  }

  "isEligible" when {
    val withMetrics = withMetricsTimerUpdate("ConsumedAPI-Agent-Mapping-eligibility-GET") _

    "agent-mapping returns a 200 response" should {
      "return true if hasEligibleEnrolments = true" in withMetrics {
        MappingStubs.givenMappingEligibilityIsEligible
        await(connector.isEligibile) shouldBe true
        MappingStubs.verifyMappingEligibilityCalled(1)
      }

      "return false if hasEligibleEnrolments = false" in withMetrics {
        MappingStubs.givenMappingEligibilityIsNotEligible
        await(connector.isEligibile) shouldBe false
        MappingStubs.verifyMappingEligibilityCalled(1)
      }
    }

    "fail with Upstream4xxException if agent-mapping fails with a 401 response" in withMetrics {
      MappingStubs.givenMappingEligibilityCheckFails(401)
      intercept[Upstream4xxResponse] { await(connector.isEligibile) }.upstreamResponseCode shouldBe 401
      MappingStubs.verifyMappingEligibilityCalled(1)
    }

    "fail with Upstream5xxException if agent-mapping fails with a 503 response" in withMetrics {
      MappingStubs.givenMappingEligibilityCheckFails(503)
      intercept[Upstream5xxResponse] { await(connector.isEligibile) }.upstreamResponseCode shouldBe 503
      MappingStubs.verifyMappingEligibilityCalled(1)
    }
  }
}