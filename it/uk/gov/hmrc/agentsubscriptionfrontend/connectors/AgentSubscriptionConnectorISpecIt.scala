package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.time.LocalDate

import com.kenshoo.play.metrics.Metrics
import org.scalatest.Assertion
import play.api.http.Status
import play.api.i18n.Lang
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr, Vrn}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.DesignatoryDetails.Person
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, BusinessDetails, SubscriptionJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionJourneyStub, AgentSubscriptionStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{validPostcode, validUtr}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, MetricTestSupport, TestData}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpClient
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
class AgentSubscriptionConnectorISpecIt extends BaseISpecIt with MetricTestSupport {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private lazy val connector: AgentSubscriptionConnector =
    new AgentSubscriptionConnector(
      app.injector.instanceOf[HttpClient],
      app.injector.instanceOf[Metrics],
      app.injector.instanceOf[AppConfig])

  private val utr = Utr("0123456789")
  private val crn = CompanyRegistrationNumber("SC123456")
  private val vrn = Vrn("888913457")
  private val dateOfReg = LocalDate.parse("2010-03-31")
  private val authProviderId = AuthProviderId("cred-12345")

  "getJourneyById" should {
    "retrieve an existing subscription journey record using the auth provider id" in {
      AgentSubscriptionJourneyStub.givenSubscriptionJourneyRecordExists(
        authProviderId,
        TestData.minimalSubscriptionJourneyRecordWithAmls(authProviderId))
      val result: Option[SubscriptionJourneyRecord] = await(connector.getJourneyById(authProviderId))
      result.get.businessDetails shouldBe BusinessDetails(SoleTrader, validUtr, Postcode(validPostcode))
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
        TestData.minimalSubscriptionJourneyRecordWithAmls(authProviderId).copy(continueId = Some("continue")))
      val result: Option[SubscriptionJourneyRecord] = await(connector.getJourneyByContinueId(ContinueId("continue")))
      result.get.businessDetails shouldBe BusinessDetails(SoleTrader, validUtr, Postcode(validPostcode))
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
      result.get.businessDetails shouldBe BusinessDetails(SoleTrader, validUtr, Postcode(validPostcode))
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
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
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
    }

    "return a not subscribed Registration when agent-subscription returns a 200 response (for a matching UTR and postcode)" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
        AgentSubscriptionStub.withMatchingUtrAndPostcode(utr, "AA1 1AA")

        val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
        result.isDefined shouldBe true
        result.get.taxpayerName shouldBe Some("My Agency")
        result.get.isSubscribedToAgentServices shouldBe false
        testBusinessAddress(result.get)
        result.get.emailAddress shouldBe Some("someone@example.com")
      }
    }

    "return a not subscribed with record in ETMP Registration when partially subscribed" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
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
    }

    "URL-path-encode path parameters" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
        AgentSubscriptionStub.withMatchingUtrAndPostcode(Utr("01234/56789"), "AA1 1AA/&")

        val result: Option[Registration] = await(connector.getRegistration(Utr("01234/56789"), "AA1 1AA/&"))
        result.isDefined shouldBe true
      }
    }

    "return None when agent-subscription returns a 404 response (for a non-matching UTR and postcode)" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
        AgentSubscriptionStub.withNonMatchingUtrAndPostcode(utr, "AA1 1AA")

        val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
        result shouldBe None
      }
    }

    "throw an exception when agent-subscription returns a 500 response" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
        AgentSubscriptionStub.withErrorForUtrAndPostcode(utr, "AA1 1AA")

        intercept[UpstreamErrorResponse] {
          await(connector.getRegistration(utr, "AA1 1AA"))
        }
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
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST") {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest)

        val result = await(connector.subscribeAgencyToMtd(subscriptionRequest))

        result shouldBe Arn("ARN00001")
      }
    }

    "throw Upstream4xxResponse if subscription already exists" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST") {
        AgentSubscriptionStub.subscriptionWillConflict(utr, subscriptionRequest)

        val e = intercept[UpstreamErrorResponse] {
          await(connector.subscribeAgencyToMtd(subscriptionRequest))
        }

        e.statusCode shouldBe 409
      }
    }

    "throw Upstream4xxResponse if postcodes don't match" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST") {
        AgentSubscriptionStub.subscriptionWillBeForbidden(utr, subscriptionRequest)

        val e = intercept[UpstreamErrorResponse] {
          await(connector.subscribeAgencyToMtd(subscriptionRequest))
        }

        e.statusCode shouldBe 403
      }
    }
  }

  "completePartialSubscription" should {
    "return an ARN when partialSubscription has been resolved" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
        AgentSubscriptionStub.partialSubscriptionWillSucceed(partialSubscriptionRequest)

        val result = await(connector.completePartialSubscription(partialSubscriptionRequest))

        result shouldBe Arn("ARN00001")
      }
    }

    "throw Upstream4xxResponse if enrolment is already allocated to someone" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
        AgentSubscriptionStub.partialSubscriptionWillReturnStatus(partialSubscriptionRequest, 409)

        val e = intercept[UpstreamErrorResponse] {
          await(connector.completePartialSubscription(partialSubscriptionRequest))
        }

        e.statusCode shouldBe 409
      }
    }

    "throw Upstream4xxResponse if details do not match any record" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
        AgentSubscriptionStub.partialSubscriptionWillReturnStatus(partialSubscriptionRequest, 403)

        val e = intercept[UpstreamErrorResponse] {
          await(connector.completePartialSubscription(partialSubscriptionRequest))
        }

        e.statusCode shouldBe 403
      }
    }

    "throw BadRequestException if BadRequest" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
        AgentSubscriptionStub.partialSubscriptionWillReturnStatus(partialSubscriptionRequest, 400)

        val e = intercept[UpstreamErrorResponse] {
          await(connector.completePartialSubscription(partialSubscriptionRequest))
        }

        e.statusCode shouldBe 400
      }
    }

    "throw Upstream5xxResponse if postcodes don't match" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
        AgentSubscriptionStub.partialSubscriptionWillReturnStatus(partialSubscriptionRequest, 500)

        val e = intercept[UpstreamErrorResponse] {
          await(connector.completePartialSubscription(partialSubscriptionRequest))
        }

        e.statusCode shouldBe 500
      }
    }
  }

  "matchCorporationTaxUtrWithCrn" should {

    "return true when agent-subscription returns a 200 response (for a matching UTR and CRN)" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-matchCorporationTaxUtrWithCrn-GET") {
        AgentSubscriptionStub
          .withMatchingCtUtrAndCrn(utr, crn)

        await(connector.matchCorporationTaxUtrWithCrn(utr, crn)) shouldBe true
      }
    }

    "return false when agent-subscription returns a 404 response (for a non-matching UTR and CRN)" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-matchCorporationTaxUtrWithCrn-GET") {
        AgentSubscriptionStub.withNonMatchingCtUtrAndCrn(utr, crn)

        await(connector.matchCorporationTaxUtrWithCrn(utr, crn)) shouldBe false
      }
    }

    "throw an exception when agent-subscription returns a 500 response" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-matchCorporationTaxUtrWithCrn-GET") {
        AgentSubscriptionStub.withErrorForCtUtrAndCrn(utr, crn)

        intercept[UpstreamErrorResponse] {
          await(connector.matchCorporationTaxUtrWithCrn(utr, crn))
        }
      }
    }
  }

  "matchVatKnownFacts" should {
    "return true when agent-subscription returns a 200 response (for a matching VRN and registration date)" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-matchVatKnownFacts-GET") {
        AgentSubscriptionStub
          .withMatchingVrnAndDateOfReg(vrn, dateOfReg)

        await(connector.matchVatKnownFacts(vrn, dateOfReg)) shouldBe true
      }
    }

    "return false when agent-subscription returns a 404 response (for a non-matching VRN and registration date)" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-matchVatKnownFacts-GET") {
        AgentSubscriptionStub.withNonMatchingVrnAndDateOfReg(vrn, dateOfReg)

        await(connector.matchVatKnownFacts(vrn, dateOfReg)) shouldBe false
      }
    }

    "throw an exception when agent-subscription returns a 500 response" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-matchVatKnownFacts-GET") {
        AgentSubscriptionStub.withErrorForVrnAndDateOfReg(vrn, dateOfReg)

        intercept[UpstreamErrorResponse] {
          await(connector.matchVatKnownFacts(vrn, dateOfReg))
        }
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
      intercept[UpstreamErrorResponse]{
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
          countryCode = "GB"),
        email = "agency@example.com"
      ),
      Some(Lang("en")),
      amlsDetails = Some(AmlsDetails("supervisory", membershipNumber = Some("123456789"), appliedOn = None, membershipExpiresOn = Some(LocalDate.now())))
    )

  private val partialSubscriptionRequest =
    CompletePartialSubscriptionBody(utr = utr, knownFacts = SubscriptionRequestKnownFacts("AA1 2AA"), langForEmail = Some(Lang("en")))
}
