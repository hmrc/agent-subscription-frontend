package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.MappingStubs
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, MetricTestSupport}
import uk.gov.hmrc.http._

class MappingConnectorISpec extends BaseISpec with MetricTestSupport {

  private implicit val hc = HeaderCarrier()

  private lazy val connector: MappingConnector =
    new MappingConnector(
      new URL(s"http://localhost:$wireMockPort"),
      app.injector.instanceOf[HttpGet with HttpPost with HttpPut with HttpDelete],
      app.injector.instanceOf[Metrics])

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

  "createPreSubscription" when {
    val withMetrics = withMetricsTimerUpdate("ConsumedAPI-Agent-Mapping-createPreSubscription-PUT") _

    "agent-mapping returns a 201 response" should {
      "return successful future" in withMetrics {
        MappingStubs.givenMappingCreatePreSubscription(Utr("1234567890"), httpReturnCode = 201)
        await(connector.createPreSubscription(Utr("1234567890")))
        MappingStubs.verifyMappingCreatePreSubscriptionCalled(Utr("1234567890"))
      }
    }

    "agent-mapping returns a 409 response" should {
      "return successful future" in withMetrics {
        MappingStubs.givenMappingCreatePreSubscription(Utr("1234567890"), httpReturnCode = 409)
        await(connector.createPreSubscription(Utr("1234567890")))
        MappingStubs.verifyMappingCreatePreSubscriptionCalled(Utr("1234567890"))
      }
    }

    "fail with Upstream4xxException if agent-mapping fails with a 401 response" in withMetrics {
      MappingStubs.givenMappingCreatePreSubscription(Utr("1234567890"), httpReturnCode = 401)
      intercept[Upstream4xxResponse] {
        await(connector.createPreSubscription(Utr("1234567890")))
      }.upstreamResponseCode shouldBe 401
      MappingStubs.verifyMappingCreatePreSubscriptionCalled(Utr("1234567890"))
    }

    "fail with Upstream4xxException if agent-mapping fails with a 403 response" in withMetrics {
      MappingStubs.givenMappingCreatePreSubscription(Utr("1234567890"), httpReturnCode = 403)
      intercept[Upstream4xxResponse] {
        await(connector.createPreSubscription(Utr("1234567890")))
      }.upstreamResponseCode shouldBe 403
      MappingStubs.verifyMappingCreatePreSubscriptionCalled(Utr("1234567890"))
    }

    "fail with Upstream5xxException if agent-mapping fails with a 503 response" in withMetrics {
      MappingStubs.givenMappingCreatePreSubscription(Utr("1234567890"), httpReturnCode = 503)
      intercept[Upstream5xxResponse] {
        await(connector.createPreSubscription(Utr("1234567890")))
      }.upstreamResponseCode shouldBe 503
      MappingStubs.verifyMappingCreatePreSubscriptionCalled(Utr("1234567890"))
    }
  }

  "updatePreSubscriptionWithArn" when {
    val withMetrics = withMetricsTimerUpdate("ConsumedAPI-Agent-Mapping-updatePreSubscriptionWithArn-PUT") _

    "agent-mapping returns a 200 response" should {
      "return successful future" in withMetrics {
        MappingStubs.givenMappingUpdateToPostSubscription(Utr("1234567890"), httpReturnCode = 200)
        await(connector.updatePreSubscriptionWithArn(Utr("1234567890")))
        MappingStubs.verifyMappingUpdateToPostSubscriptionCalled(Utr("1234567890"))
      }
    }

    "agent-mapping returns a 401 response" should {
      "fail with Upstream4xxException" in withMetrics {
        MappingStubs.givenMappingUpdateToPostSubscription(Utr("1234567890"), httpReturnCode = 401)
        intercept[Upstream4xxResponse] {
          await(connector.updatePreSubscriptionWithArn(Utr("1234567890")))
        }.upstreamResponseCode shouldBe 401
        MappingStubs.verifyMappingUpdateToPostSubscriptionCalled(Utr("1234567890"))
      }
    }

    "agent-mapping returns a 403 response" should {
      "fail with Upstream4xxException" in withMetrics {
        MappingStubs.givenMappingUpdateToPostSubscription(Utr("1234567890"), httpReturnCode = 403)
        intercept[Upstream4xxResponse] {
          await(connector.updatePreSubscriptionWithArn(Utr("1234567890")))
        }.upstreamResponseCode shouldBe 403
        MappingStubs.verifyMappingUpdateToPostSubscriptionCalled(Utr("1234567890"))
      }
    }

    "agent-mapping returns a 503 response" should {
      "fail with Upstream5xxException" in withMetrics {
        MappingStubs.givenMappingUpdateToPostSubscription(Utr("1234567890"), httpReturnCode = 503)
        intercept[Upstream5xxResponse] {
          await(connector.updatePreSubscriptionWithArn(Utr("1234567890")))
        }.upstreamResponseCode shouldBe 503
        MappingStubs.verifyMappingUpdateToPostSubscriptionCalled(Utr("1234567890"))
      }
    }
  }

  "deletePreSubscriptionWithArn" when {
    val withMetrics = withMetricsTimerUpdate("ConsumedAPI-Agent-Mapping-deletePreSubscription-DELETE") _

    "agent-mapping returns a 204 response" should {
      "return successful future" in withMetrics {
        MappingStubs.givenMappingDeletePreSubscription(Utr("1234567890"), httpReturnCode = 204)
        await(connector.deletePreSubscription(Utr("1234567890")))
        MappingStubs.verifyMappingDeletePreSubscriptionCalled(Utr("1234567890"))
      }
    }

    "agent-mapping returns a 401 response" should {
      "fail with Upstream4xxException" in withMetrics {
        MappingStubs.givenMappingDeletePreSubscription(Utr("1234567890"), httpReturnCode = 401)
        intercept[Upstream4xxResponse] {
          await(connector.deletePreSubscription(Utr("1234567890")))
        }.upstreamResponseCode shouldBe 401
        MappingStubs.verifyMappingDeletePreSubscriptionCalled(Utr("1234567890"))
      }
    }

    "agent-mapping returns a 503 response" should {
      "fail with Upstream5xxException" in withMetrics {
        MappingStubs.givenMappingDeletePreSubscription(Utr("1234567890"), httpReturnCode = 503)
        intercept[Upstream5xxResponse] {
          await(connector.deletePreSubscription(Utr("1234567890")))
        }.upstreamResponseCode shouldBe 503
        MappingStubs.verifyMappingDeletePreSubscriptionCalled(Utr("1234567890"))
      }
    }
  }
}