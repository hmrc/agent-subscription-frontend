package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscriptionfrontend.config.HttpVerbs
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.WireMockSupport
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class AgentAssuranceConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport{

  private implicit val hc = HeaderCarrier()

  private lazy val connector = new AgentAssuranceConnector(new URL(s"http://localhost:$wireMockPort"), app.injector.instanceOf[HttpVerbs])

  "getRegistration PAYE" should {
    "return true when the current logged in user has an acceptable number of PAYE clients" in {
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      await(connector.hasAcceptableNumberOfPayeClients) shouldBe true
    }

    "return false when the current logged in user does not have an acceptable number of PAYE clients" in {
      givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients
      await(connector.hasAcceptableNumberOfPayeClients) shouldBe false
    }

    "return false when the current user is not authenticated" in {
      givenUserIsNotAuthenticatedForPAYEClientCheck
      await(connector.hasAcceptableNumberOfPayeClients) shouldBe false
    }

    "throw an exception when appropriate" in {
      givenAnExceptionOccursDuringThePAYEClientCheck
      intercept[Exception] {
        await(connector.hasAcceptableNumberOfPayeClients)
      }
    }
  }

  "getRegistration SA" should {
    "return true when the current logged in user has an acceptable number of SA clients" in {
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients
      await(connector.hasAcceptableNumberOfSAClients) shouldBe true
    }

    "return false when the current logged in user does not have an acceptable number of SA clients" in {
      givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients
      await(connector.hasAcceptableNumberOfSAClients) shouldBe false
    }

    "return false when the current user is not authenticated" in {
      givenUserIsNotAuthenticatedForSAClientCheck
      await(connector.hasAcceptableNumberOfSAClients) shouldBe false
    }

    "throw an exception when appropriate" in {
      givenAnExceptionOccursDuringTheSAClientCheck
      intercept[Exception] {
        await(connector.hasAcceptableNumberOfSAClients)
      }
    }
  }

  "hasActiveCesaRelationship" should {
    "receie 200 if valid combination passed and relationship exists in Cesa Nino" in {
      givenNinoAGoodCombinationAndUserHasRelationshipInCesa("nino", "AA123456A", "SA6012")
      await(connector.hasActiveCesaRelationship("nino", "AA123456A", SaAgentReference("SA6012"))) shouldBe true
    }
    "receie 200 if valid combination passed and relationship exists in Cesa Utr" in {
      givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")
      await(connector.hasActiveCesaRelationship("utr", "4000000009", SaAgentReference("SA6012"))) shouldBe true
    }
    "receive 403 if valid combination passed and relationship does not exist in Cesa" in {
      givenAUserDoesNotHaveRelationshipInCesa("nino", "AA123456A", "SA6012")
      await(connector.hasActiveCesaRelationship("nino", "AA123456A", SaAgentReference("SA6012"))) shouldBe false
    }
    "receive 403 if invalid combination passed" in {
      givenABadCombinationAndUserHasRelationshipInCesa("nino", "A23456A", "SA126013")
      await(connector.hasActiveCesaRelationship("nino", "A23456A", SaAgentReference("SA126013"))) shouldBe false
    }
  }
}
