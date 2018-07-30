package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping

object MappingStubs {
  val urlEligibility = "/agent-mapping/mapping/eligibility"

  def givenMappingEligibilityIsEligible: StubMapping =
    stubFor(
      get(urlEqualTo(urlEligibility))
        .willReturn(ok("""{ "hasEligibleEnrolments" : true }""")))

  def givenMappingEligibilityIsNotEligible: StubMapping =
    stubFor(
      get(urlEqualTo(urlEligibility))
        .willReturn(ok("""{ "hasEligibleEnrolments" : false }""")))

  def givenMappingEligibilityCheckFails(httpReturnCode: Int): StubMapping =
    stubFor(
      get(urlEqualTo(urlEligibility))
        .willReturn(status(httpReturnCode)))

  def verifyMappingEligibilityCalled(times: Int = 1) = verify(times, getRequestedFor(urlEqualTo(urlEligibility)))
}
