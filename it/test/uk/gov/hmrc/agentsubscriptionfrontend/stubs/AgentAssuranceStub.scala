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
import com.github.tomakehurst.wiremock.matching.UrlPattern
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

  def getUtrDetailsUrlPattern(utr: String): UrlPattern = urlEqualTo(s"/agent-assurance/managed-utrs/utr/$utr")

  def givenUtrDetails(utr: String, isManuallyAssured: Boolean = false, isRefusalToDealWith: Boolean = false): StubMapping =
    stubFor(
      get(getUtrDetailsUrlPattern(utr)).willReturn(
        aResponse()
          .withBody(
            // language=JSON
            s"""
               |{
               | "isManuallyAssured": $isManuallyAssured,
               | "isRefusalToDealWith": $isRefusalToDealWith
               |}
               |""".stripMargin
          )
          .withStatus(200)
      )
    )

  def givenAgentIsNotOnRefusalToDealWithUtrList(utr: String): StubMapping =
    givenUtrDetails(utr, isRefusalToDealWith = false, isManuallyAssured = true)

  def givenUtrIsNotManaged(utr: String): StubMapping = givenUtrDetails(utr)

  def verifyCheckRefusalToDealWith(times: Int, utr: String) =
    verify(times, getRequestedFor(getUtrDetailsUrlPattern(utr)))

  val retrieveAmlsDataUrl = (utr: String) => urlEqualTo(s"/agent-assurance/amls/utr/$utr")

  def givenAgentIsNotManuallyAssured(utr: String): StubMapping = givenUtrDetails(utr, isRefusalToDealWith = true, isManuallyAssured = false)

  def givenAgentIsManuallyAssured(utr: String): StubMapping =
    givenUtrDetails(utr, isRefusalToDealWith = false, isManuallyAssured = true)

  def givenAgentIsOnRefusalToDealList(utr: String): StubMapping =
    givenUtrDetails(utr, isRefusalToDealWith = true, isManuallyAssured = false)

  def givenAmlsDataIsFound(utr: String): StubMapping =
    stubFor(
      get(retrieveAmlsDataUrl(utr))
        .willReturn(
          aResponse()
            .withBody(
              // language=JSON
              """
                |{
                | "supervisoryBody": "HMRC",
                | "appliedOn": "2020-10-20"
                |}
                |""".stripMargin
            )
            .withStatus(200)
        )
    )

  def givenAmlsDataIsFoundWithoutAppliedOn(utr: String): StubMapping =
    stubFor(
      get(retrieveAmlsDataUrl(utr))
        .willReturn(
          aResponse()
            .withBody(
              // language=JSON
              """
                |{
                | "supervisoryBody": "HM Revenue and Customs (HMRC)"
                |}
                |""".stripMargin
            )
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
      get(getUtrDetailsUrlPattern(utr))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

  def verifyCheckAgentIsManuallyAssured(times: Int, utr: String) =
    verify(times, getRequestedFor(getUtrDetailsUrlPattern(utr)))

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
