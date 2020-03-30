package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.BusinessDetails
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AuthProviderId, ContactEmailData, Postcode, Registration}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestData}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.{givenNoSubscriptionJourneyRecordExists, givenSubscriptionJourneyRecordExists, givenSubscriptionRecordCreated}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{subscribingAgentEnrolledForHMRCASAGENT, subscribingAgentEnrolledForNonMTD}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{businessAddress, registrationName, validPostcode, validUtr}
import play.api.test.Helpers._
import org.jsoup.Jsoup
import uk.gov.hmrc.http.BadRequestException


class ContactDetailsControllerISpec extends BaseISpec{

  lazy val controller: ContactDetailsController = app.injector.instanceOf[ContactDetailsController]
  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  val id = AuthProviderId("12345-credId")


  "showContactEmailCheck (GET /contact-email-check) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showContactEmailCheck(_))

    "200 OK with correct message content when subscriptionJourneyRecord exists with businessEmail in registration" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = Some(Registration(
                Some(registrationName),
                isSubscribedToAgentServices = false,
                isSubscribedToETMP = true,
                businessAddress,
                Some("test@gmail.com")))
            )
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showContactEmailCheck(request))

      result should containMessages(
        "contactEmailCheck.title",
        "contactEmailCheck.option.differentEmail",
        "contactEmailCheck.continue.button"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val businessEmailRadio = doc.getElementById("check-yes")
      val anotherEmailRadio = doc.getElementById("check-no")

      businessEmailRadio.hasAttr("checked") shouldBe false
      anotherEmailRadio.hasAttr("checked") shouldBe false
    }

    "200 OK with correct message content and radio button selected when subscriptionJourneyRecord exists " +
      "with businessEmail in registration and contactEmail data exists" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = Some(Registration(
                Some(registrationName),
                isSubscribedToAgentServices = false,
                isSubscribedToETMP = true,
                businessAddress,
                Some("test@gmail.com")))
            ),
            contactEmailData = Some(ContactEmailData(true, Some("test@gmail.com")))
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showContactEmailCheck(request))

      result should containMessages(
        "contactEmailCheck.title",
        "contactEmailCheck.option.differentEmail",
        "contactEmailCheck.continue.button"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val businessEmailRadio = doc.getElementById("check-yes")
      val anotherEmailRadio = doc.getElementById("check-no")

      businessEmailRadio.hasAttr("checked") shouldBe true
      anotherEmailRadio.hasAttr("checked") shouldBe false
    }

    "303 Redirect to /task-list when no business email found in record" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = Some(Registration(
                Some(registrationName),
                isSubscribedToAgentServices = false,
                isSubscribedToETMP = true,
                businessAddress,
                None)
            )
          )))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showContactEmailCheck(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.start().url)
    }
  }

  "submitContactEmailCheck (POST /contact-email-check) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showContactEmailCheck(_))

    "303 redirect to /task-list when same as business email selected" in {

      givenSubscriptionJourneyRecordExists(id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitContactEmailCheck(request.withFormUrlEncodedBody("check" -> "yes")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.start().url)
    }

    "303 redirect to /contact-email-address when a different business email is selected" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com")
          )
          )),
        contactEmailData = Some(ContactEmailData(true, None)))

      givenSubscriptionJourneyRecordExists(id, sjr)

//      givenSubscriptionRecordCreated(id,sjr.copy(
//        contactEmailData = Some(ContactEmailData(true, Some("email@email.com")))
//      ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitContactEmailCheck(request.withFormUrlEncodedBody("check" -> "no")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showContactEmailAddress().url)
    }

    "200 OK when a invalid entry is submitted" in {

      givenSubscriptionJourneyRecordExists(id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
          businessDetails = BusinessDetails(SoleTrader,
            validUtr,
            Postcode(validPostcode),
            registration = Some(Registration(
              Some(registrationName),
              isSubscribedToAgentServices = false,
              isSubscribedToETMP = true,
              businessAddress,
              Some("email@email.com")
            )
            ))))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      intercept[BadRequestException] {
        await(controller.submitContactEmailCheck(request.withFormUrlEncodedBody("check" -> "INVALID")))
      }.getMessage should be("Strange input value")
      }

  }


  }
