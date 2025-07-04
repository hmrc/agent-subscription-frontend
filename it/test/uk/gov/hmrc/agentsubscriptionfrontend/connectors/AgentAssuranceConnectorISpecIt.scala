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

package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.UtrDetails
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, MetricTestSupport}
import uk.gov.hmrc.domain.{Nino, SaAgentReference}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AgentAssuranceConnectorISpecIt extends BaseISpecIt with MetricTestSupport {

  private implicit val request: RequestHeader = FakeRequest()

  private lazy val connector =
    new AgentAssuranceConnector(
      app.injector.instanceOf[HttpClientV2],
      app.injector.instanceOf[Metrics],
      app.injector.instanceOf[AppConfig]
    )

  "getRegistration PAYE" should {
    behave like testAcceptableNumberOfClientsEndpoint("IR-PAYE")(connector.hasAcceptableNumberOfPayeClients)
  }

  "getRegistration SA" should {
    behave like testAcceptableNumberOfClientsEndpoint("IR-SA")(connector.hasAcceptableNumberOfSAClients)
  }

  "getRegistration HMCE-VATDEC-ORG" should {
    behave like testAcceptableNumberOfClientsEndpoint("HMCE-VATDEC-ORG")(connector.hasAcceptableNumberOfVatDecOrgClients)
  }

  "getRegistration IR-CT" should {
    behave like testAcceptableNumberOfClientsEndpoint("IR-CT")(connector.hasAcceptableNumberOfIRCTClients)
  }

  "hasActiveCesaRelationship" should {
    "receive 200 if valid combination passed and relationship exists in Cesa Nino" in {
      withMetricsTimerUpdate("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
        givenNinoAGoodCombinationAndUserHasRelationshipInCesa("nino", "AA123456A", "SA6012")
        await(connector.hasActiveCesaRelationship(Nino("AA123456A"), "nino", SaAgentReference("SA6012"))) shouldBe true
      }
    }

    "receive 200 if valid combination passed and relationship exists in Cesa Utr" in {
      withMetricsTimerUpdate("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
        givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")
        await(connector.hasActiveCesaRelationship(Utr("4000000009"), "utr", SaAgentReference("SA6012"))) shouldBe true
      }
    }

    "receive 403 if valid combination passed and relationship does not exist in Cesa" in {
      withMetricsTimerUpdate("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
        givenAUserDoesNotHaveRelationshipInCesa("nino", "AA123456A", "SA6012")
        await(connector.hasActiveCesaRelationship(Nino("AA123456A"), "nino", SaAgentReference("SA6012"))) shouldBe false
      }
    }

    "receive 403 if invalid combination passed" in {
      withMetricsTimerUpdate("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
        givenABadCombinationAndUserHasRelationshipInCesa("nino", "AB123456A", "SA126013")
        await(connector.hasActiveCesaRelationship(Nino("AB123456A"), "nino", SaAgentReference("SA126013"))) shouldBe false
      }
    }

    "receive 404 when valid Nino but is not found in DB" in {
      withMetricsTimerUpdate("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
        givenAGoodCombinationAndNinoNotFoundInCesa("nino", "AB123456B", "SA126012")
        await(connector.hasActiveCesaRelationship(Nino("AB123456A"), "nino", SaAgentReference("SA126013"))) shouldBe false
      }
    }
  }

  "getR2DWAgents" should {
    val utr = "2000000009"
    "return true is utr found in r2dw list" in {
      givenAgentIsOnRefusalToDealList(utr)
      val result = connector.getUtrDetails(utr).futureValue
      result.isRefusalToDealWith shouldBe true
    }
    "return false is utr not found in r2dw list" in {
      givenAgentIsNotOnRefusalToDealWithUtrList(utr)
      connector.getUtrDetails(utr).futureValue.isRefusalToDealWith shouldBe false
    }

    "return not managed utr not found in any list" in {
      val utr1 = "1234567"
      givenUtrIsNotManaged(utr1)
      connector.getUtrDetails(utr1).futureValue shouldBe UtrDetails(isManuallyAssured = false, isRefusalToDealWith = false)
    }

  }

  "getAmlsData" should {
    val utr = "2000000009"

    "return AMLS data when agent-assurance responds with 200" in {
      givenAmlsDataIsFound(utr)
      val maybeAmlsDetails = await(connector.getAmlsData(utr))
      maybeAmlsDetails.isDefined shouldBe true
    }

    "return AMLS data when agent-assurance responds with 200 without appliedOn" in {
      givenAmlsDataIsFoundWithoutAppliedOn(utr)
      val maybeAmlsDetails = await(connector.getAmlsData(utr))
      maybeAmlsDetails.get.isPending shouldBe true
      maybeAmlsDetails.get.amlsSafeId shouldBe None
    }

    "return None when agent-assurance response with 400" in {
      givenAmlsDataIsNotFound(utr)
      val maybeAmlsDetails = await(connector.getAmlsData(utr))
      maybeAmlsDetails.isDefined shouldBe false
    }
  }

  "getManuallyAssuredAgents" should {
    val utr = "2000000009"
    "return true is utr found in the manually assured agents list" in {
      givenAgentIsManuallyAssured(utr)
      connector.getUtrDetails(utr).futureValue.isManuallyAssured shouldBe true
    }
    "return false if utr not found in the manually assured agents list" in {
      givenAgentIsNotManuallyAssured(utr)
      connector.getUtrDetails(utr).futureValue.isManuallyAssured shouldBe false
    }

    "throw illegal state exception when agent-assurance responds with 404" in {
      givenManuallyAssuredAgentsReturns(utr, 404)
      intercept[UpstreamErrorResponse] {
        await(connector.getUtrDetails(utr))
      }
    }
    "throw Upstream4xxResponse when agent-assurance responds with 401" in {
      givenManuallyAssuredAgentsReturns(utr, 401)
      intercept[UpstreamErrorResponse] {
        await(connector.getUtrDetails(utr))
      }
    }
    "throw Upstream5xxResponse when agent-assurance responds with 500" in {
      givenManuallyAssuredAgentsReturns(utr, 500)
      intercept[UpstreamErrorResponse] {
        await(connector.getUtrDetails(utr))
      }
    }
    "monitor with metric ConsumedAPI-AgentAssurance-getManuallyAssuredAgents-GET" in {
      withMetricsTimerUpdate("ConsumedAPI-AgentAssurance-getAgentChecks-GET") {
        givenAgentIsManuallyAssured(utr)
        connector.getUtrDetails(utr).futureValue
      }
    }
  }

  def testAcceptableNumberOfClientsEndpoint(service: String)(method: => Future[Boolean]): Unit = {
    s"return true when the current logged in user has an acceptable number of $service clients" in {
      givenUserIsAnAgentWithAnAcceptableNumberOfClients(service)
      await(method) shouldBe true
    }

    s"return false when the current logged in user does not have an acceptable number of $service clients" in {
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients(service)
      await(method) shouldBe false
    }

    s"return false when the current user is not authenticated for $service" in {
      givenUserIsNotAuthenticatedForClientCheck(service)
      await(method) shouldBe false
    }

    s"throw an exception when appropriate for $service" in {
      givenAnExceptionOccursDuringTheClientCheck(service)
      intercept[Exception] {
        await(method)
      }
    }
  }
}
