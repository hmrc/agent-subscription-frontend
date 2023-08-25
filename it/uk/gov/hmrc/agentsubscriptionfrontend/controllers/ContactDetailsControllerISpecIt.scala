package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import org.jsoup.Jsoup
import play.api.test.Helpers
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.BusinessDetails
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AddressLookupFrontendStubs._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.{givenSubscriptionJourneyRecordExists, givenSubscriptionRecordCreated}
import uk.gov.hmrc.agentsubscriptionfrontend.support.Css.ERROR_SUMMARY_LINK
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{businessAddress, phoneNumber, registrationName, validPostcode, validUtr}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestData}
import uk.gov.hmrc.http.BadRequestException

import scala.concurrent.Future


class ContactDetailsControllerISpecIt extends BaseISpecIt {

  lazy val controller: ContactDetailsController = app.injector.instanceOf[ContactDetailsController]
  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  val id = AuthProviderId("12345-credId")

  val returnFromAddressLookupUrl: String = routes.ContactDetailsController.returnFromAddressLookup().url

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
                Some("test@gmail.com"),
                Some(phoneNumber),
                Some("safeId")))
            )
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showContactEmailCheck(request))

      result should containMessages(
        "contactEmailCheck.title",
        "contactEmailCheck.p",
        "contactEmailCheck.option.yes",
        "contactEmailCheck.option.no",
        "button.continue"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val businessEmailRadio = doc.getElementById("check")
      val anotherEmailRadio = doc.getElementById("check-2")

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
                Some("test@gmail.com"),
                Some(phoneNumber),
                Some("safeId")))
            ),
            contactEmailData = Some(ContactEmailData(useBusinessEmail = true, Some("test@gmail.com")))
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showContactEmailCheck(request))

      result should containMessages(
        "contactEmailCheck.title",
        "contactEmailCheck.p",
        "contactEmailCheck.option.yes",
        "contactEmailCheck.option.no",
        "button.continue"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val businessEmailRadio = doc.getElementById("check")
      val anotherEmailRadio = doc.getElementById("check-2")

      businessEmailRadio.hasAttr("checked") shouldBe true
      anotherEmailRadio.hasAttr("checked") shouldBe false
    }

    "303 Redirect to /start when no business email found in record" in {

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
                None,
                None,
                Some("safeId"))
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

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId"))
          )
        ))

      givenSubscriptionJourneyRecordExists(id, sjr)

      givenSubscriptionRecordCreated(id, sjr.copy(
        contactEmailData = Some(ContactEmailData(useBusinessEmail = true, Some("email@email.com"))))
      )

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      val result =
        await(controller.submitContactEmailCheck(request.withFormUrlEncodedBody("check" -> "yes")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
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
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)
      givenSubscriptionRecordCreated(id, sjr.copy(
        contactEmailData = Some(ContactEmailData(useBusinessEmail = false, None))
      ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      val result =
        await(controller.submitContactEmailCheck(request.withFormUrlEncodedBody("check" -> "no")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showContactEmailAddress().url)
    }

    "200 OK with error messages when submit without making a choice" in {
      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      val result =
        await(controller.submitContactEmailCheck(request))

      status(result) shouldBe 200

      result should containMessages("error.contact-email-check.invalid")
  }

  "throw a BadRequestException when invalid entry is submitted" in {

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
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          )
          ))))

    val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

    intercept[BadRequestException] {
      await(controller.submitContactEmailCheck(request.withFormUrlEncodedBody("check" -> "INVALID")))
    }.getMessage should be("Strange form input value")
  }
}

  "showContactEmailAddress (GET /contact-email-address) " should {
  behave like anAgentAffinityGroupOnlyEndpoint (controller.showContactEmailCheck (_) )

  "200 OK with correct message content when subscriptionJourneyRecord exists and email-check visited" in {

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
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          )
          )),
        contactEmailData = Some(ContactEmailData(useBusinessEmail = true, None))))

    val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

    val result =
      await(controller.showContactEmailAddress(request))

    status(result) shouldBe 200

    result should containMessages("contactEmailAddress.title",
      "contactEmailAddress.p",
      "contactEmailAddress.button")

    result should containLink("button.back",routes.ContactDetailsController.showContactEmailCheck().url)
  }

    "303 Redirect to /contact-email-check when no contact email data found" in {

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
              Some("email@email.com"),
              Some(phoneNumber),
              Some("safeId")
            )
            ))))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      val result =
        await(controller.showContactEmailAddress(request))

      status(result) shouldBe 303
     redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showContactEmailCheck().url)
    }
  }

  "submitContactEmailAddress (POST /contact-email-address) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showContactEmailCheck(_))

    "303 redirect to /task-list when submit with valid email address" in {
      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
            )
          )),
        contactEmailData = Some(ContactEmailData(useBusinessEmail = true, None)))

      givenSubscriptionJourneyRecordExists(id, sjr)
      givenSubscriptionRecordCreated(id, sjr.copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
            )
          )),
          contactEmailData = Some(ContactEmailData(useBusinessEmail = true, Some("new@email.com"))))
        )
        val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

        val result =
          await(controller.submitContactEmailAddress(request.withFormUrlEncodedBody("email" -> "new@email.com")))

        status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
    }

    "200 OK with error message with empty submission" in {
      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          )
          )),
        contactEmailData = Some(ContactEmailData(useBusinessEmail = true, None)))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      val result =
        await(controller.submitContactEmailAddress(request.withFormUrlEncodedBody("email" -> "")))

      status(result) shouldBe 200

      result should containMessages("contactEmailAddress.title",
        "error.contact-email.empty")
    }

    "200 OK with error message with email that's too long submission" in {
      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          )
          )),
        contactEmailData = Some(ContactEmailData(useBusinessEmail = true, None)))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      val result =
        await(controller.submitContactEmailAddress(request.withFormUrlEncodedBody("email" -> TestData.emailTooLong)))

      status(result) shouldBe 200

      val html = Jsoup.parse(Helpers.contentAsString(Future.successful(result)))

      html.title() shouldBe "Error: What is the email address you want to use for your agent services account? - Create an agent services account - GOV.UK"
      html.select(ERROR_SUMMARY_LINK).text() shouldBe "Email address must be 132 characters or fewer"

      result should containMessages("contactEmailAddress.title",
        "error.contact-email.maxLength")
    }

    "200 OK with error message with email that's invalid" in {
      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          )
          )),
        contactEmailData = Some(ContactEmailData(useBusinessEmail = true, None)))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      val result =
        await(controller.submitContactEmailAddress(request.withFormUrlEncodedBody("email" -> "^$$%@email.$$*&com")))

      status(result) shouldBe 200

      result should containMessages("contactEmailAddress.title",
        "error.contact-email.format")

      val html = Jsoup.parse(Helpers.contentAsString(Future.successful(result)))

      html.title() shouldBe "Error: What is the email address you want to use for your agent services account? - Create an agent services account - GOV.UK"
      html.select(ERROR_SUMMARY_LINK).text() shouldBe "Enter an email address in the correct format, like name@example.com"
    }
  }

  "showTradingNameCheck (GET /trading-name) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showTradingNameCheck(_))

    "200 OK with correct message content when subscriptionJourneyRecord exists with taxpayerName in registration" in {

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
                Some("test@gmail.com"),
                Some(phoneNumber),
                Some("safeId")))
            )
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showTradingNameCheck(request))

      result should containMessages(
       "contactTradingNameCheck.title",
        "contactTradingNameCheck.option.yes",
        "contactTradingNameCheck.option.no",
        "button.continue"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val tradingNameRadioYes = doc.getElementById("check")
      val tradingNameRadioNo = doc.getElementById("check-2")

      tradingNameRadioYes.hasAttr("checked") shouldBe false
      tradingNameRadioNo.hasAttr("checked") shouldBe false
    }

    "200 OK with correct message content and radio button selected when subscriptionJourneyRecord exists " +
      "with taxpayerName in registration and contactTradingNameData exists" in {

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
                Some("test@gmail.com"),
                Some(phoneNumber),
                Some("safeId")))
            ),
            contactEmailData = Some(ContactEmailData(useBusinessEmail = true, Some("test@gmail.com"))),
            contactTradingNameData = Some(ContactTradingNameData(hasTradingName = true, Some(registrationName)))
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showTradingNameCheck(request))

      result should containMessages(
        "contactTradingNameCheck.title",
        "contactTradingNameCheck.option.yes",
        "contactTradingNameCheck.option.no",
        "button.continue"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val tradingNameRadioYes = doc.getElementById("check")
      val tradingNameRadioNo = doc.getElementById("check-2")

      tradingNameRadioYes.hasAttr("checked") shouldBe true
      tradingNameRadioNo.hasAttr("checked") shouldBe false
    }

    "303 Redirect to /start when no taxpayerName found in record" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = Some(Registration(
                None,
                isSubscribedToAgentServices = false,
                isSubscribedToETMP = true,
                businessAddress,
                None,
                None,
                Some("safeId"))
              )
            )))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showTradingNameCheck(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.start().url)
    }
  }

  "submitTradingNameCheck (POST /trading-name) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitTradingNameCheck(_))

    "303 redirect to /main-trading-name when No selected" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId"))
          )
        ))

      givenSubscriptionJourneyRecordExists(id, sjr)

      givenSubscriptionRecordCreated(id, sjr.copy(
        contactTradingNameData = Some(ContactTradingNameData(hasTradingName = false, None)))
      )

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      val result =
        await(controller.submitTradingNameCheck(request.withFormUrlEncodedBody("check" -> "no")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showTradingName().url)
    }

    "303 redirect to /task-list when Yes is selected" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)
      givenSubscriptionRecordCreated(id, sjr.copy(
        contactTradingNameData = Some(ContactTradingNameData(hasTradingName = true, None))
      ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      val result =
        await(controller.submitTradingNameCheck(request.withFormUrlEncodedBody("check" -> "yes")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
    }

    "200 OK with error messages when submit without making a choice" in {
      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      val result =
        await(controller.submitTradingNameCheck(request))

      status(result) shouldBe 200

      result should containMessages("error.contact-trading-name-check.invalid")
    }

    "throw a BadRequestException when invalid entry is submitted" in {

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
              Some("email@email.com"),
              Some(phoneNumber),
              Some("safeId")
            )
            ))))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      intercept[BadRequestException] {
        await(controller.submitTradingNameCheck(request.withFormUrlEncodedBody("check" -> "INVALID")))
      }.getMessage should be("Strange form input value")
    }
  }

  "showCheckMainTradingAddress (GET /check-trading-address) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showCheckMainTradingAddress(_))

    "200 OK with correct message content when subscriptionJourneyRecord exists with address in registration" in {

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
                Some("test@gmail.com"),
                Some(phoneNumber),
                Some("safeId")))
            )
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showCheckMainTradingAddress(request))

      result should containMessages(
        "contactTradingAddressCheck.title",
        "contactTradingAddressCheck.option.yes",
        "contactTradingAddressCheck.option.no",
        "button.continue"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val tradingNameRadioYes = doc.getElementById("check")
      val tradingNameRadioNo = doc.getElementById("check-2")

      tradingNameRadioYes.hasAttr("checked") shouldBe false
      tradingNameRadioNo.hasAttr("checked") shouldBe false
    }

    "200 OK with correct message content and radio button selected when subscriptionJourneyRecord exists " +
      "with address in registration and contactTradingAddressData exists" in {

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
                Some("test@gmail.com"),
                Some(phoneNumber),
                Some("safeId")))
            ),
            contactEmailData = Some(ContactEmailData(useBusinessEmail = true, Some("test@gmail.com"))),
            contactTradingNameData = Some(ContactTradingNameData(hasTradingName = true, Some(registrationName))),
            contactTradingAddressData = Some(ContactTradingAddressData(useBusinessAddress = true, Some(businessAddress)))
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showCheckMainTradingAddress(request))

      result should containMessages(
        "contactTradingAddressCheck.title",
        "contactTradingAddressCheck.option.yes",
        "contactTradingAddressCheck.option.no",
        "button.continue"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val tradingNameRadioYes = doc.getElementById("check")
      val tradingNameRadioNo = doc.getElementById("check-2")

      tradingNameRadioYes.hasAttr("checked") shouldBe true
      tradingNameRadioNo.hasAttr("checked") shouldBe false
    }

    "303 Redirect to /start when no registration found in record" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = None
              )
            ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showCheckMainTradingAddress(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.start().url)
    }
  }

  "submitCheckMainTradingAddress (POST /check-main-trading-address) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitCheckMainTradingAddress(_))

    "303 redirect to /task-list when Yes selected" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId"))
          )
        ))

      givenSubscriptionJourneyRecordExists(id, sjr)

      givenSubscriptionRecordCreated(id, sjr.copy(
        contactTradingAddressData = Some(ContactTradingAddressData(useBusinessAddress = true, Some(businessAddress))))
      )

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      val result =
        await(controller.submitCheckMainTradingAddress(request.withFormUrlEncodedBody("check" -> "yes")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
    }

    "303 redirect to /main-trading-address when No is selected" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)
      givenSubscriptionRecordCreated(id, sjr.copy(
        contactTradingAddressData = Some(ContactTradingAddressData(useBusinessAddress = false, None))
      ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      val result =
        await(controller.submitCheckMainTradingAddress(request.withFormUrlEncodedBody("check" -> "no")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showMainTradingAddress().url)
    }

    "200 OK with error messages when submit without making a choice" in {
      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      val result =
        await(controller.submitCheckMainTradingAddress(request))

      status(result) shouldBe 200

      result should containMessages("error.contact-trading-address-check.invalid")
    }

    "throw a BadRequestException when invalid entry is submitted" in {

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
              Some("email@email.com"),
              Some(phoneNumber),
              Some("safeId")
            )
            ))))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)

      intercept[BadRequestException] {
        await(controller.submitCheckMainTradingAddress(request.withFormUrlEncodedBody("check" -> "INVALID")))
      }.getMessage should be("Strange form input value")
    }
  }

  "showMainTradingAddress (GET /find-main-trading-address)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showMainTradingAddress(_))


    "303 redirect to specified location if init journey at address-lookup-frontend was successful" in {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenAddressLookupInit( returnFromAddressLookupUrl)
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showMainTradingAddress(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(returnFromAddressLookupUrl)
    }
  }

  "returnFromAddressLookup (GET /lookup-trading-address)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.returnFromAddressLookup("")(_))

    "303 redirect to /task-list after successful address lookup" in {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenAddressLookupReturnsAddress("address-id")
      givenSubscriptionRecordCreated(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        contactTradingAddressData = Some(ContactTradingAddressData(useBusinessAddress = true, Some(
         BusinessAddress("10 Other Place", Some("Some District"), Some("Line 3"), Some("Sometown"), Some("AA1 1AA"), "GB")
          )
        ))
      ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.returnFromAddressLookup("address-id")(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
    }
  }

  "GET /contact-phone-check" should {
    "redirect to /check-telephone-number when business partner record contains a primaryPhoneNumber" in {

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
              Some("email@email.com"),
              Some(phoneNumber),
              Some("safeId")
            )
            ))))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, GET)

      val result = await(controller.contactPhoneCheck(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showCheckTelephoneNumber.url)
    }

    "redirect to /telephone-number when business partner record does not contain a primaryPhoneNumber" in {

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
              Some("email@email.com"),
              None,
              Some("safeId")
            )
            ))))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, GET)

      val result = await(controller.contactPhoneCheck(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showTelephoneNumber.url)
    }
  }

  "GET /check-telephone-number" should {
    "200 OK when primaryPhoneNumber is on business partner record" in {

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
              Some("email@email.com"),
              Some(phoneNumber),
              Some("safeId")
            )
            ))))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, GET)
      val result = await(controller.showCheckTelephoneNumber(request))

      status(result) shouldBe OK

      result should containMessages(
        "contactPhoneCheck.title",
        "contactPhoneCheck.yes",
        "contactPhoneCheck.no",
        "button.continue"
      )
    }

    "303 SEE_OTHER when primaryPhoneNumber is not on the business partner record" in {

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
              Some("email@email.com"),
              None,
              Some("safeId")
            )
            ))))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, GET)
      val result = await(controller.showCheckTelephoneNumber(request))

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showTelephoneNumber.url)
    }
  }

  "POST /check-telephone-number" should {
    "303 SEE_OTHER to /task-list when Yes is selected" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)
      givenSubscriptionRecordCreated(id, sjr.copy(
        contactTelephoneData = Some(ContactTelephoneData(true, Some(phoneNumber)))
      ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
      val result = await(controller.submitCheckTelephoneNumber(request.withFormUrlEncodedBody("check" -> "yes")))

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
    }

    "303 SEE_OTHER to /telephone-number when No is selected" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)
      givenSubscriptionRecordCreated(id, sjr.copy(
        contactTelephoneData = Some(ContactTelephoneData(false, None))
      ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
      val result = await(controller.submitCheckTelephoneNumber(request.withFormUrlEncodedBody("check" -> "no")))

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showTelephoneNumber.url)
    }

    "200 OK with error when empty submit" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
      val result = await(controller.submitCheckTelephoneNumber(request.withFormUrlEncodedBody("check" -> "")))

      status(result) shouldBe OK

      result should containMessages(
        "contactPhoneCheck.title",
        "contactPhoneCheck.yes",
        "contactPhoneCheck.no",
        "button.continue",
        "error.contact-phone-check.invalid"
      )
    }
  }

  "GET /telephone-number" should {
    "200 OK" in {

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
              Some("email@email.com"),
              None,
              Some("safeId")
            )
            ))))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, GET)
      val result = await(controller.showTelephoneNumber(request))

      status(result) shouldBe 200

      result should containMessages(
        "contactTelephone.title",
        "contactTelephone.p"
      )
    }
  }

  "POST /telephone-number" should {
    "303 SEE_OTHER to /task-list when valid telephone number entered" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)
      givenSubscriptionRecordCreated(id, sjr.copy(
        contactTelephoneData = Some(ContactTelephoneData(false, Some("01273111111")))
      ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
      val result = await(controller.submitTelephoneNumber(request.withFormUrlEncodedBody("telephone" -> "01273111111")))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)

    }

    "200 OK with error-empty when nothing submitted" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
      val result = await(controller.submitTelephoneNumber(request.withFormUrlEncodedBody("telephone" -> "")))

      status(result) shouldBe OK

      result should containMessages(
        "contactTelephone.title",
        "contactTelephone.p",
        "error.contact.phone.empty"
      )
    }

    "200 OK with error-invalid when not a UK number submitted" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"),
            Some(phoneNumber),
            Some("safeId")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST)
      val result = await(controller.submitTelephoneNumber(request.withFormUrlEncodedBody("telephone" -> "+112121111111")))

      status(result) shouldBe OK

      result should containMessages(
        "contactTelephone.title",
        "contactTelephone.p",
        "error.contact.phone.invalid"
      )
    }
  }


  }
