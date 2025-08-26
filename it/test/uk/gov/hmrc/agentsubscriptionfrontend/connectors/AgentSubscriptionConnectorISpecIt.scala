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

import org.scalatest.Assertion
import play.api.http.Status
import play.api.i18n.Lang
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.models.{Arn, Vrn}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.DesignatoryDetails.Person
import uk.gov.hmrc.agentsubscriptionfrontend.models.FormBundleStatus.Approved
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, BusinessDetails, SubscriptionJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionJourneyStub, AgentSubscriptionStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{phoneNumber, validPostcode, validUtr}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestData}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.{LocalDate, Month}
import scala.concurrent.ExecutionContext.Implicits.global
class AgentSubscriptionConnectorISpecIt extends BaseISpecIt {

  private implicit val request: RequestHeader = FakeRequest()

  private lazy val connector: AgentSubscriptionConnector =
    new AgentSubscriptionConnector(app.injector.instanceOf[HttpClientV2], app.injector.instanceOf[Metrics], app.injector.instanceOf[AppConfig])

  private val utr = "0123456789"
  private val crn = CompanyRegistrationNumber("SC123456")
  private val vrn = Vrn("888913457")
  private val dateOfReg = LocalDate.parse("2010-03-31")
  private val authProviderId = AuthProviderId("cred-12345")

  "getJourneyById" should {
    "retrieve an existing subscription journey record using the auth provider id" in {
      AgentSubscriptionJourneyStub.givenSubscriptionJourneyRecordExists(
        authProviderId,
        TestData.minimalSubscriptionJourneyRecordWithAmls(authProviderId)
      )
      val result: Option[SubscriptionJourneyRecord] = await(connector.getJourneyById(authProviderId))
      result.get.businessDetails shouldBe BusinessDetails(SoleTrader, validUtr, validPostcode)
      result.get.amlsData shouldBe Some(AmlsData.registeredUserNoDataEntered)
    }

    "return None when there is no existing subscription journey record associated with the auth provider id" in {
      AgentSubscriptionJourneyStub.givenNoSubscriptionJourneyRecordExists(authProviderId)
      val result: Option[SubscriptionJourneyRecord] = await(connector.getJourneyById(authProviderId))
      result shouldBe None
    }
  }

  "getJourneyByContinueId" should {
    "retrieve an existing subscription journey record using the continue id" in {
      AgentSubscriptionJourneyStub.givenSubscriptionJourneyRecordExists(
        ContinueId("continue"),
        TestData.minimalSubscriptionJourneyRecordWithAmls(authProviderId).copy(continueId = Some("continue"))
      )
      val result: Option[SubscriptionJourneyRecord] = await(connector.getJourneyByContinueId(ContinueId("continue")))
      result.get.businessDetails shouldBe BusinessDetails(SoleTrader, validUtr, validPostcode)
      result.get.amlsData shouldBe Some(AmlsData.registeredUserNoDataEntered)
    }

    "return None when there is no existing subscription journey record associated with the auth provider id" in {
      AgentSubscriptionJourneyStub.givenNoSubscriptionJourneyRecordExists(ContinueId("continue"))
      val result: Option[SubscriptionJourneyRecord] = await(connector.getJourneyByContinueId(ContinueId("continue")))
      result shouldBe None
    }
  }

  "getJourneyByUtr" should {
    "retrieve an existing subscription journey record using a utr" in {
      AgentSubscriptionJourneyStub.givenSubscriptionJourneyRecordExists(
        validUtr,
        TestData.minimalSubscriptionJourneyRecordWithAmls(authProviderId)
      )
      val result: Option[SubscriptionJourneyRecord] = await(connector.getJourneyByUtr(validUtr))
      result.get.businessDetails shouldBe BusinessDetails(SoleTrader, validUtr, validPostcode)
      result.get.amlsData shouldBe Some(AmlsData.registeredUserNoDataEntered)
    }
    "return None when there is no existing subscription journey record associated with a utr" in {
      AgentSubscriptionJourneyStub.givenNoSubscriptionJourneyRecordExists(validUtr)
      val result: Option[SubscriptionJourneyRecord] = await(connector.getJourneyByUtr(validUtr))
      result shouldBe None
    }
  }

  "createOrUpdateJourney" should {
    "return 204 when a record is successfully created" in {
      AgentSubscriptionJourneyStub
        .givenSubscriptionRecordCreated(authProviderId, TestData.minimalSubscriptionJourneyRecord(authProviderId))
      val result = await(connector.createOrUpdateJourney(TestData.minimalSubscriptionJourneyRecord(authProviderId)))

      result shouldBe 204
    }

    "throw a runtime exception when the endpoint returns a bad request" in {
      AgentSubscriptionJourneyStub
        .givenSubscriptionRecordNotCreated(authProviderId, TestData.minimalSubscriptionJourneyRecord(authProviderId), Status.BAD_REQUEST)
      intercept[UpstreamErrorResponse] {
        await(connector.createOrUpdateJourney(TestData.minimalSubscriptionJourneyRecord(authProviderId)))
      }
    }
  }

  "getRegistration" should {

    "return a subscribed Registration when agent-subscription returns a 200 response (for a matching UTR and postcode)" in {
      AgentSubscriptionStub
        .withMatchingUtrAndPostcode(utr, "AA1 1AA", isSubscribedToAgentServices = true, isSubscribedToETMP = true)
      val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
      result.isDefined shouldBe true
      result.get.taxpayerName shouldBe Some("My Agency")
      result.get.isSubscribedToAgentServices shouldBe true
      result.get.isSubscribedToETMP shouldBe true
      testBusinessAddress(result.get)
      result.get.emailAddress shouldBe Some("someone@example.com")
    }

    "return a not subscribed Registration when agent-subscription returns a 200 response (for a matching UTR and postcode)" in {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(utr, "AA1 1AA")

      val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
      result.isDefined shouldBe true
      result.get.taxpayerName shouldBe Some("My Agency")
      result.get.isSubscribedToAgentServices shouldBe false
      testBusinessAddress(result.get)
      result.get.emailAddress shouldBe Some("someone@example.com")

    }

    "return a not subscribed with record in ETMP Registration when partially subscribed" in {
      AgentSubscriptionStub
        .withMatchingUtrAndPostcode(utr, "AA1 1AA", isSubscribedToETMP = true)

      val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
      result.isDefined shouldBe true
      result.get.taxpayerName shouldBe Some("My Agency")
      result.get.isSubscribedToETMP shouldBe true
      result.get.isSubscribedToAgentServices shouldBe false
      testBusinessAddress(result.get)
      result.get.emailAddress shouldBe Some("someone@example.com")
    }

    "URL-path-encode path parameters" in {
      AgentSubscriptionStub.withMatchingUtrAndPostcode("01234/56789", "AA1 1AA/&")

      val result: Option[Registration] = await(connector.getRegistration("01234/56789", "AA1 1AA/&"))
      result.isDefined shouldBe true
    }

    "return None when agent-subscription returns a 404 response (for a non-matching UTR and postcode)" in {
      AgentSubscriptionStub.withNonMatchingUtrAndPostcode(utr, "AA1 1AA")

      val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
      result shouldBe None

    }

    "throw an exception when agent-subscription returns a 500 response" in {
      AgentSubscriptionStub.withErrorForUtrAndPostcode(utr, "AA1 1AA")

      intercept[UpstreamErrorResponse] {
        await(connector.getRegistration(utr, "AA1 1AA"))
      }
    }

    def testBusinessAddress(registration: Registration): Assertion = {
      registration.address.addressLine1 shouldBe "AddressLine1 A"
      registration.address.addressLine2 shouldBe Some("AddressLine2 A")
      registration.address.addressLine3 shouldBe Some("AddressLine3 A")
      registration.address.addressLine4 shouldBe Some("AddressLine4 A")
    }
  }

  "subscribe" should {
    "return an ARN" in {
      AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest)

      val result = await(connector.subscribeAgencyToMtd(subscriptionRequest))

      result shouldBe Arn("ARN00001")

    }

    "throw Upstream4xxResponse if subscription already exists" in {
      AgentSubscriptionStub.subscriptionWillConflict(utr, subscriptionRequest)

      val e = intercept[UpstreamErrorResponse] {
        await(connector.subscribeAgencyToMtd(subscriptionRequest))
      }

      e.statusCode shouldBe 409

    }

    "throw Upstream4xxResponse if postcodes don't match" in {
      AgentSubscriptionStub.subscriptionWillBeForbidden(utr, subscriptionRequest)

      val e = intercept[UpstreamErrorResponse] {
        await(connector.subscribeAgencyToMtd(subscriptionRequest))
      }

      e.statusCode shouldBe 403
    }

  }

  "completePartialSubscription" should {
    "return an ARN when partialSubscription has been resolved" in {
      AgentSubscriptionStub.partialSubscriptionWillSucceed(partialSubscriptionRequest)

      val result = await(connector.completePartialSubscription(partialSubscriptionRequest))

      result shouldBe Arn("ARN00001")

    }

    "throw Upstream4xxResponse if enrolment is already allocated to someone" in {
      AgentSubscriptionStub.partialSubscriptionWillReturnStatus(partialSubscriptionRequest, 409)

      val e = intercept[UpstreamErrorResponse] {
        await(connector.completePartialSubscription(partialSubscriptionRequest))
      }

      e.statusCode shouldBe 409

    }

    "throw Upstream4xxResponse if details do not match any record" in {
      AgentSubscriptionStub.partialSubscriptionWillReturnStatus(partialSubscriptionRequest, 403)

      val e = intercept[UpstreamErrorResponse] {
        await(connector.completePartialSubscription(partialSubscriptionRequest))
      }

      e.statusCode shouldBe 403
    }

    "throw BadRequestException if BadRequest" in {
      AgentSubscriptionStub.partialSubscriptionWillReturnStatus(partialSubscriptionRequest, 400)

      val e = intercept[UpstreamErrorResponse] {
        await(connector.completePartialSubscription(partialSubscriptionRequest))
      }

      e.statusCode shouldBe 400

    }

    "throw Upstream5xxResponse if postcodes don't match" in {
      AgentSubscriptionStub.partialSubscriptionWillReturnStatus(partialSubscriptionRequest, 500)

      val e = intercept[UpstreamErrorResponse] {
        await(connector.completePartialSubscription(partialSubscriptionRequest))
      }

      e.statusCode shouldBe 500
    }

  }

  "matchCorporationTaxUtrWithCrn" should {

    "return true when agent-subscription returns a 200 response (for a matching UTR and CRN)" in {
      AgentSubscriptionStub
        .withMatchingCtUtrAndCrn(utr, crn)

      await(connector.matchCorporationTaxUtrWithCrn(utr, crn)) shouldBe true

    }

    "return false when agent-subscription returns a 404 response (for a non-matching UTR and CRN)" in {
      AgentSubscriptionStub.withNonMatchingCtUtrAndCrn(utr, crn)

      await(connector.matchCorporationTaxUtrWithCrn(utr, crn)) shouldBe false
    }

    "throw an exception when agent-subscription returns a 500 response" in {
      AgentSubscriptionStub.withErrorForCtUtrAndCrn(utr, crn)

      intercept[UpstreamErrorResponse] {
        await(connector.matchCorporationTaxUtrWithCrn(utr, crn))
      }

    }
  }

  "matchVatKnownFacts" should {
    "return true when agent-subscription returns a 200 response (for a matching VRN and registration date)" in {
      AgentSubscriptionStub
        .withMatchingVrnAndDateOfReg(vrn, dateOfReg)

      await(connector.matchVatKnownFacts(vrn, dateOfReg)) shouldBe true

    }

    "return false when agent-subscription returns a 404 response (for a non-matching VRN and registration date)" in {
      AgentSubscriptionStub.withNonMatchingVrnAndDateOfReg(vrn, dateOfReg)

      await(connector.matchVatKnownFacts(vrn, dateOfReg)) shouldBe false

    }

    "throw an exception when agent-subscription returns a 500 response" in {
      AgentSubscriptionStub.withErrorForVrnAndDateOfReg(vrn, dateOfReg)

      intercept[UpstreamErrorResponse] {
        await(connector.matchVatKnownFacts(vrn, dateOfReg))
      }

    }
  }

  "GET /citizen-details/nino/designatory-details" should {
    val nino = Nino("XX121212B")
    val dob = DateOfBirth(LocalDate.now)
    val lastName = "Matchmaker"

    "return DesignatoryDetails if found for a given nino" in {
      AgentSubscriptionStub.givenDesignatoryDetailsForNino(nino, Some(lastName), dob)
      await(connector.getDesignatoryDetails(nino)) shouldBe DesignatoryDetails(Some(Person(Some(lastName), Some(dob), deceased = Some(false))))
    }

    "handle the case when DesignatoryDetails are not found for a given nino" in {
      AgentSubscriptionStub.givenDesignatoryDetailsReturnsStatus(nino, 404)
      await(connector.getDesignatoryDetails(nino)) shouldBe DesignatoryDetails()
    }

    "throw a Upstream5xxResponse error when there is a network problem" in {
      AgentSubscriptionStub.givenDesignatoryDetailsReturnsStatus(nino, 500)
      val e = intercept[UpstreamErrorResponse] {
        await(connector.getDesignatoryDetails(nino))
      }

      e.statusCode shouldBe 500
    }
  }

  "getAmlsSubscriptionRecord" should {
    "return amls Subscription Record" in {
      AgentSubscriptionStub.givenAmlsRecordFound("12345", Approved)
      val result = await(connector.getAmlsSubscriptionRecord("12345"))

      result shouldBe Some(
        AmlsSubscriptionRecord(Approved, "111234567890123", Some(LocalDate.of(2021, Month.JANUARY, 1)), Some(LocalDate.now().plusDays(2)), None)
      )
    }

    " return None when agent subscription record returns a 404 response" in {
      AgentSubscriptionStub.givenAmlsRecordNotFound("12345")
      val result = await(connector.getAmlsSubscriptionRecord("12345"))

      result shouldBe None
    }

    "throw an UpstreamErrorResponse exception when agent subscription returns an unexpected status" in {
      AgentSubscriptionStub.givenAmlsRecordNonSuceesfulCase("12345", 400)

      intercept[UpstreamErrorResponse] {
        await(connector.getAmlsSubscriptionRecord("12345"))
      }
    }
  }

  import AgentSubscriptionStub.givenCompaniesHouseStatusCheck
  "companiesHouseStatusCheck" should {
    "return OK if the company has active status" in {
      givenCompaniesHouseStatusCheck(crn.value, OK)
      await(connector.companiesHouseStatusCheck(crn)) shouldBe OK
    }
    "return CONFLICT if the company has inactive status" in {
      givenCompaniesHouseStatusCheck(crn.value, CONFLICT)
      await(connector.companiesHouseStatusCheck(crn)) shouldBe CONFLICT
    }
    "return NOT_FOUND if the company does not exist" in {
      givenCompaniesHouseStatusCheck(crn.value, NOT_FOUND)
      await(connector.companiesHouseStatusCheck(crn)) shouldBe NOT_FOUND
    }
    "throw exception if there was a problemn with the service" in {
      givenCompaniesHouseStatusCheck(crn.value, SERVICE_UNAVAILABLE)
      intercept[UpstreamErrorResponse] {
        await(connector.companiesHouseStatusCheck(crn))
      }
    }
  }

  private val subscriptionRequest =
    SubscriptionRequest(
      utr = utr,
      knownFacts = SubscriptionRequestKnownFacts("AA1 2AA"),
      agency = Agency(
        name = "My Agency",
        address = DesAddress(
          addressLine1 = "1 Some Street",
          addressLine2 = Some("Anytown"),
          addressLine3 = None,
          addressLine4 = None,
          postcode = "AA1 1AA",
          countryCode = "GB"
        ),
        email = "agency@example.com",
        telephone = Some(phoneNumber)
      ),
      Some(Lang("en")),
      amlsDetails =
        Some(AmlsDetails("supervisory", membershipNumber = Some("123456789"), appliedOn = None, membershipExpiresOn = Some(LocalDate.now())))
    )

  private val partialSubscriptionRequest =
    CompletePartialSubscriptionBody(utr = utr, knownFacts = SubscriptionRequestKnownFacts("AA1 2AA"), langForEmail = Some(Lang("en")))
}
