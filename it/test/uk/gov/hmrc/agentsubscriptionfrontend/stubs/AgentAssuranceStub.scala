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

package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping

object AgentAssuranceStub {
  def checkForAcceptableNumberOfClientsUrl(service: String) = s"/agent-assurance/acceptableNumberOfClients/service/$service"

  def givenUserIsAnAgentWithAnAcceptableNumberOfClients(service: String): StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfClientsUrl(service))).willReturn(aResponse().withStatus(204)))

  def givenUserIsNotAnAgentWithAnAcceptableNumberOfClients(service: String): StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfClientsUrl(service))).willReturn(aResponse().withStatus(403)))

  def givenUserIsNotAuthenticatedForClientCheck(service: String): StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfClientsUrl(service))).willReturn(aResponse().withStatus(401)))

  def givenAnExceptionOccursDuringTheClientCheck(service: String): StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfClientsUrl(service))).willReturn(aResponse().withStatus(404)))

  def verifyCheckForAcceptableNumberOfClients(service: String, times: Int) =
    verify(times, getRequestedFor(urlEqualTo(checkForAcceptableNumberOfClientsUrl(service))))
  val agentChecksUrl = (utr: String) => urlEqualTo(s"/agent-assurance/restricted-collection-check/utr/$utr")

  def givenCustomAgentChecks(utr: String, isManuallyAssured: Boolean, isRefusalToDealWith: Boolean): StubMapping =
    stubFor(
      get(agentChecksUrl(utr)).willReturn(
        aResponse()
          .withBody(s"""
                       |{
                       | "isManuallyAssured": $isManuallyAssured,
                       | "isRefusalToDealWith": $isRefusalToDealWith
                       |}
                       |""".stripMargin)
          .withStatus(200)
      )
    )

  def givenAgentIsOnRefusalToDealList(utr: String): StubMapping =
    stubFor(
      get(agentChecksUrl(utr)).willReturn(
        aResponse()
          .withBody("""
                      |{
                      | "isManuallyAssured": true,
                      | "isRefusalToDealWith": true
                      |}
                      |""".stripMargin)
          .withStatus(200)
      )
    )

  def givenAgentIsNotOnRefusalToDealWithUtrList(utr: String): StubMapping =
    stubFor(
      get(agentChecksUrl(utr)).willReturn(
        aResponse()
          .withBody("""
                      |{
                      | "isManuallyAssured": false,
                      | "isRefusalToDealWith": false
                      |}
                      |""".stripMargin)
          .withStatus(200)
      )
    )

  def givenRefusalToDealWithReturns404(utr: String): StubMapping =
    stubFor(get(agentChecksUrl(utr)).willReturn(aResponse().withStatus(404)))

  def verifyCheckRefusalToDealWith(times: Int, utr: String) =
    verify(times, getRequestedFor(urlEqualTo(s"$agentChecksUrl/$utr")))

  val retrieveAmlsDataUrl = (utr: String) => urlEqualTo(s"/agent-assurance/amls/utr/$utr")

  def givenAgentIsNotManuallyAssured(utr: String): StubMapping =
    stubFor(
      get(agentChecksUrl(utr))
        .willReturn(
          aResponse()
            .withBody("""
                        |{
                        | "isManuallyAssured": false,
                        | "isRefusalToDealWith": false
                        |}
                        |""".stripMargin)
            .withStatus(200)
        )
    )

  def givenAgentIsManuallyAssured(utr: String): StubMapping =
    stubFor(
      get(agentChecksUrl(utr))
        .willReturn(
          aResponse()
            .withBody("""
                        |{
                        | "isManuallyAssured": true,
                        | "isRefusalToDealWith": true
                        |}
                        |""".stripMargin)
            .withStatus(200)
        )
    )

  def givenAmlsDataIsFound(utr: String): StubMapping =
    stubFor(
      get(retrieveAmlsDataUrl(utr))
        .willReturn(
          aResponse()
            .withBody("""
                        |{
                        | "supervisoryBody": "HMRC",
                        | "appliedOn": "2020-10-20"
                        |}
                        |""".stripMargin)
            .withStatus(200)
        )
    )

  def givenAmlsDataIsFoundWithoutAppliedOn(utr: String): StubMapping =
    stubFor(
      get(retrieveAmlsDataUrl(utr))
        .willReturn(
          aResponse()
            .withBody("""
                        |{
                        | "supervisoryBody": "HM Revenue and Customs (HMRC)"
                        |}
                        |""".stripMargin)
            .withStatus(200)
        )
    )

  def givenAmlsDataIsNotFound(utr: String): StubMapping =
    stubFor(
      get(retrieveAmlsDataUrl(utr))
        .willReturn(aResponse().withStatus(404))
    )

  def givenManuallyAssuredAgentsReturns(utr: String, status: Int): StubMapping =
    stubFor(
      get(agentChecksUrl(utr))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

  def verifyCheckAgentIsManuallyAssured(times: Int, utr: String) =
    verify(times, getRequestedFor(agentChecksUrl(utr)))

  def givenNinoAGoodCombinationAndUserHasRelationshipInCesa(ninoOrUtr: String, valueOfNinoOrUtr: String, saAgentReference: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/agent-assurance/activeCesaRelationship/nino/AA123456A/saAgentReference/SA6012"))
        .willReturn(aResponse().withStatus(200))
    )

  def givenUtrAGoodCombinationAndUserHasRelationshipInCesa(ninoOrUtr: String, valueOfNinoOrUtr: String, saAgentReference: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/agent-assurance/activeCesaRelationship/utr/4000000009/saAgentReference/SA6012"))
        .willReturn(aResponse().withStatus(200))
    )

  def givenAUserDoesNotHaveRelationshipInCesa(ninoOrUtr: String, valueOfNinoOrUtr: String, saAgentReference: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/agent-assurance/activeCesaRelationship/$ninoOrUtr/$valueOfNinoOrUtr/saAgentReference/$saAgentReference"))
        .willReturn(aResponse().withStatus(403))
    )

  def givenABadCombinationAndUserHasRelationshipInCesa(ninoOrUtr: String, valueOfNinoOrUtr: String, saAgentReference: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/agent-assurance/activeCesaRelationship/$ninoOrUtr/$valueOfNinoOrUtr/saAgentReference/$saAgentReference"))
        .willReturn(aResponse().withStatus(403))
    )

  def givenAGoodCombinationAndNinoNotFoundInCesa(ninoOrUtr: String, valueOfNinoOrUtr: String, saAgentReference: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/agent-assurance/activeCesaRelationship/$ninoOrUtr/$valueOfNinoOrUtr/saAgentReference/$saAgentReference"))
        .willReturn(aResponse().withStatus(404))
    )

}
