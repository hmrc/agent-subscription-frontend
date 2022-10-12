/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.time.LocalDate
import org.jsoup.Jsoup
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.config.amls.AMLSLoader
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.FormBundleStatus.{Approved, Rejected}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub.givenAgentIsNotManuallyAssured
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.{givenNoSubscriptionJourneyRecordExists, givenSubscriptionJourneyRecordExists, givenSubscriptionRecordCreated}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{subscribingAgentEnrolledForNonMTD, subscribingCleanAgentWithoutEnrolments}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestData}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.{givenAmlsRecordFound, givenAmlsRecordNotFound}
import uk.gov.hmrc.http.HeaderCarrier

class AMLSControllerISpec extends BaseISpec {

  lazy val controller: AMLSController = app.injector.instanceOf[AMLSController]
  val appConfig = app.injector.instanceOf[AppConfig]

  val utr = Utr("2000000000")
  val businessAddress =
    BusinessAddress(
      "AddressLine1 A",
      Some("AddressLine2 A"),
      Some("AddressLine3 A"),
      Some("AddressLine4 A"),
      Some("AA11AA"),
      "GB")

  val validAmlsRegistrationNumber = "XAML00000200000"

  trait Setup {
    implicit def authenticatedRequest(method: String = GET): FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(
      subscribingCleanAgentWithoutEnrolments, method)
    givenAgentIsNotManuallyAssured(utr.value)
    givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecord(id))
  }

  trait SetupUnclean {
    implicit val authenticatedRequest: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(
      subscribingAgentEnrolledForNonMTD)
    givenAgentIsNotManuallyAssured(utr.value)
    givenNoSubscriptionJourneyRecordExists(id)
  }

  "GET /change-amls" should {

    "redirect to the amls registered page and cache changing as true" in {
      implicit val authenticatedRequest: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(
        subscribingAgentEnrolledForNonMTD)
      val result = await(controller.changeAmlsDetails(authenticatedRequest))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AMLSController.showAmlsRegisteredPage().url)
      sessionStoreService.currentSession.changingAnswers shouldBe Some(true)
    }
  }

  "GET /check-money-laundering-compliance" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showAmlsRegisteredPage(_))

    "contain page with expected content" in new Setup {
      val result = await(controller.showAmlsRegisteredPage(authenticatedRequest()))

      result should containMessages(
        "check-amls.title",
        "button.yes",
        "button.no"
      )
      result should containSubmitButton("button.saveContinue","check-amls-continue")
      result should containSubmitButton("button.saveComeBackLater","check-amls-save")
    }

    "pre-populate radio button on page when it is present in the BE store" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))

      val result = await(controller.showAmlsRegisteredPage(authenticatedRequest()))

      result should containMessages(
        "check-amls.title",
        "button.yes",
        "button.no"
      )
    }

    "throw exception when no journey record found" in new Setup {
      givenNoSubscriptionJourneyRecordExists(id)
      intercept[RuntimeException] {
        await(controller.showAmlsRegisteredPage(authenticatedRequest()))
      }.getMessage should be("Expected Journey Record missing")
    }

  }

  "POST /check-money-laundering-compliance" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitAmlsRegistered(_))

    "redirect to /money-laundering-compliance when user selects yes and continues" in new Setup {
      givenSubscriptionRecordCreated(id, record.copy(amlsData = Some(AmlsData.registeredUserNoDataEntered)))

      val result =
        await(controller.submitAmlsRegistered(authenticatedRequest("POST").withFormUrlEncodedBody("registeredAmls" -> "yes", "submit" -> "continue")))

      println(bodyOf(result))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AMLSController.showAmlsDetailsForm().url)
    }
    "redirect to /progress-saved when user selects yes and save and come back later" in new Setup {
      givenSubscriptionRecordCreated(id, record.copy(amlsData = Some(AmlsData.registeredUserNoDataEntered)))

      val result =
        await(controller.submitAmlsRegistered(authenticatedRequest(POST).withFormUrlEncodedBody("registeredAmls" -> "yes", "submit" -> "save")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.savedProgress(Some(routes.AMLSController.showAmlsRegisteredPage().url)).url)
    }

    "redirect to /check-money-laundering-application when user selects no and continues" in new Setup {
      givenSubscriptionRecordCreated(id, record.copy(amlsData = Some(AmlsData.nonRegisteredUserNoDataEntered)))

      val result =
        await(controller.submitAmlsRegistered(authenticatedRequest(POST).withFormUrlEncodedBody("registeredAmls" -> "no", "submit" -> "continue")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AMLSController.showCheckAmlsAlreadyAppliedForm().url)
    }

    "redirect to /money-laundering-compliance page and clear amls data in store when field is pre-populated with no " +
      "but user changes answer to yes and continues" in new Setup {
      val completeAmlsData = AmlsData(
        false,
        None,
        Some(AmlsDetails("Insolvency Practitioners Association (IPA)", Right(RegisteredDetails("123456789", Some(LocalDate.now()))))))

      givenSubscriptionJourneyRecordExists(id, record.copy(amlsData = Some(completeAmlsData)))
      givenSubscriptionRecordCreated(
        id,
        record.copy(amlsData = Some(AmlsData(amlsRegistered = true, None, None))))

      val result =
        await(controller.submitAmlsRegistered(authenticatedRequest(POST).withFormUrlEncodedBody("registeredAmls" -> "yes", "submit" -> "continue")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AMLSController.showAmlsDetailsForm().url)
    }

    "redirect to /progress-saved page and clear amls data in store when field is pre-populated with no " +
      "but user changes answer to yes and saves" in new Setup {
      val completeAmlsData = AmlsData(
        amlsRegistered = false,
        None,
        Some(AmlsDetails("Insolvency Practitioners Association (IPA)", Right(RegisteredDetails("123456789", Some(LocalDate.now()))))))

      givenSubscriptionJourneyRecordExists(id, record.copy(amlsData = Some(completeAmlsData)))
      givenSubscriptionRecordCreated(
        id,
        record.copy(amlsData = Some(AmlsData(amlsRegistered = true, None, None))))

      val result =
        await(controller.submitAmlsRegistered(authenticatedRequest(POST).withFormUrlEncodedBody("registeredAmls" -> "yes", "submit" -> "save")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.savedProgress(Some(routes.AMLSController.showAmlsRegisteredPage().url)).url)
    }

    "redirect to /money-laundering-compliance page and clear amls data in store when user submits pre-populated field and continues" in new Setup {
      val completeAmlsData = AmlsData(
        amlsRegistered = true,
        None,
        Some(AmlsDetails("Insolvency Practitioners Association (IPA)", Right(RegisteredDetails("123456789", Some(LocalDate.now()))))))

      givenSubscriptionJourneyRecordExists(id, record.copy(amlsData = Some(completeAmlsData)))
      givenSubscriptionRecordCreated(id, record.copy(amlsData = Some(completeAmlsData)))

      val result =
        await(controller.submitAmlsRegistered(authenticatedRequest(POST).withFormUrlEncodedBody("registeredAmls" -> "yes", "submit" -> "continue")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AMLSController.showAmlsDetailsForm().url)
    }

    "handle form with errors - user does not make a choice and tries to continue" in new Setup {
      val result =
        await(controller.submitAmlsRegistered(authenticatedRequest(POST).withFormUrlEncodedBody("registeredAmls" -> "")))

      status(result) shouldBe 200

      result should containMessages(
        "check-amls.title",
        "button.yes",
        "button.no",
        "error.check-amls-value.invalid"
      )
    }

    "handle form with errors - user manipulates the value and tries to continue" in new Setup {
      val result =
        await(controller.submitAmlsRegistered(authenticatedRequest(POST).withFormUrlEncodedBody("registeredAmls" -> "blah")))

      status(result) shouldBe 200

      result should containMessages(
        "check-amls.title",
        "button.yes",
        "button.no",
        "error.check-amls-value.invalid"
      )
    }
  }

  "GET /check-money-laundering-application" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showCheckAmlsAlreadyAppliedForm(_))

    "contain page with expected content" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))

      val result = await(controller.showCheckAmlsAlreadyAppliedForm(authenticatedRequest()))

      result should containMessages(
        "amlsAppliedFor.title",
        "button.yes",
        "button.no"
      )

      result should containSubmitButton("button.saveContinue","amls-applied-for-continue")
      result should containSubmitButton("button.saveComeBackLater","amls-applied-for-save")
    }

    "pre-populate radio button field when it exists in BE store" in new Setup {
      givenSubscriptionJourneyRecordExists(
        id,
        TestData
          .minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(amlsData = Some(AmlsData(amlsRegistered = false, amlsAppliedFor = Some(true), None))))

      val result = await(controller.showCheckAmlsAlreadyAppliedForm(authenticatedRequest()))

      result should containMessages(
        "amlsAppliedFor.title",
        "button.yes",
        "button.no"
      )
    }
  }

  "POST /check-money-laundering-application" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitCheckAmlsAlreadyAppliedForm(_))

    "redirect to /money-laundering-application-details when user selects yes and continues" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenSubscriptionRecordCreated(
        id,
        record.copy(amlsData = Some(AmlsData.registeredUserNoDataEntered.copy(amlsAppliedFor = Some(true)))))

      val result = await(
        controller.submitCheckAmlsAlreadyAppliedForm(
          authenticatedRequest(POST).withFormUrlEncodedBody("amlsAppliedFor" -> "yes", "submit" -> "continue")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AMLSController.showAmlsApplicationDatePage().url)
    }

    "redirect to /progress-saved when user selects yes and saves" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenSubscriptionRecordCreated(
        id,
        record.copy(amlsData = Some(AmlsData.registeredUserNoDataEntered.copy(amlsAppliedFor = Some(true)))))

      val result = await(
        controller.submitCheckAmlsAlreadyAppliedForm(
          authenticatedRequest(POST).withFormUrlEncodedBody("amlsAppliedFor" -> "yes", "submit" -> "save")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.savedProgress(Some(routes.AMLSController.showCheckAmlsAlreadyAppliedForm().url)).url)
    }

    "redirect to /money-laundering-compliance-incomplete when user selects no and continues" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenSubscriptionRecordCreated(
        id,
        record.copy(amlsData = Some(AmlsData.registeredUserNoDataEntered.copy(amlsAppliedFor = Some(false)))))

      val result = await(
        controller.submitCheckAmlsAlreadyAppliedForm(
          authenticatedRequest(POST).withFormUrlEncodedBody("amlsAppliedFor" -> "no", "submit" -> "continue")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AMLSController.showAmlsNotAppliedPage().url)
    }

    "redirect to /progress-saved when user selects no and saves" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenSubscriptionRecordCreated(
        id,
        record.copy(amlsData = Some(AmlsData.registeredUserNoDataEntered.copy(amlsAppliedFor = Some(false)))))

      val result = await(
        controller.submitCheckAmlsAlreadyAppliedForm(
          authenticatedRequest(POST).withFormUrlEncodedBody("amlsAppliedFor" -> "no", "submit" -> "save")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.savedProgress(Some(routes.AMLSController.showCheckAmlsAlreadyAppliedForm().url)).url)
    }

    "throw a RuntimeException when there is no AMLS data found" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecord(id))

      intercept[RuntimeException] {
        await(
          controller.submitCheckAmlsAlreadyAppliedForm(
            authenticatedRequest(POST).withFormUrlEncodedBody("amlsAppliedFor" -> "yes")))
      }.getMessage shouldBe "No AMLS data found in record"
    }

    "handle form with errors - user does not make a choice and tries to continue" in new Setup {
      val result = await(
        controller.submitCheckAmlsAlreadyAppliedForm(
          authenticatedRequest(POST).withFormUrlEncodedBody("amlsAppliedFor" -> "")))

      status(result) shouldBe 200

      result should containMessages(
        "amlsAppliedFor.title",
        "button.yes",
        "button.no",
        "error.check-amlsAppliedFor-value.invalid"
      )
    }

    "handle form with errors - user manipulates the value and tries to continue" in new Setup {
      val result = await(
        controller.submitCheckAmlsAlreadyAppliedForm(
          authenticatedRequest(POST).withFormUrlEncodedBody("amlsAppliedFor" -> "blah")))

      status(result) shouldBe 200

      result should containMessages(
        "amlsAppliedFor.title",
        "button.yes",
        "button.no",
        "error.check-amlsAppliedFor-value.invalid"
      )
    }
  }

  "showAmlsDetailsForm (GET /money-laundering-compliance)" should {

    behave like anAgentAffinityGroupOnlyEndpoint(controller.showAmlsDetailsForm(_))

    "contain page titles and content" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))

      val result = await(controller.showAmlsDetailsForm(authenticatedRequest()))

      result should containMessages(
        "moneyLaunderingCompliance.title",
        "moneyLaunderingCompliance.p1"
      )

      result should containSubmitButton("button.saveContinue","amls-details-continue")
      result should containSubmitButton("button.saveComeBackLater","amls-details-save")
    }

    "ask for a money laundering supervisory body name from a list of acceptable values" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))

      val result = await(controller.showAmlsDetailsForm(authenticatedRequest()))
      result should containMessages("moneyLaunderingCompliance.amls.title")

      val doc = Jsoup.parse(bodyOf(result))

      val elAmlsSelect = doc.getElementById("amlsCode")
      elAmlsSelect should not be null
      elAmlsSelect.tagName() shouldBe "select"

      val amlsBodies = AMLSLoader.load("/amls.csv")
      amlsBodies.foreach {
        case (expectedCode, expectedName) => {
          val elChoice = elAmlsSelect.getElementById(s"amlsCode-$expectedCode")
          elChoice should not be null
          elChoice.tagName() shouldBe "option"
          elChoice.attr("value") shouldBe expectedCode
          elChoice.text() shouldBe expectedName
        }
      }
    }

    "ask for membership number" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))

      val result = await(controller.showAmlsDetailsForm(authenticatedRequest()))

      result should containMessages("moneyLaunderingCompliance.membershipNumber.title")
      result should containInputElement("membershipNumber", "text")
    }

    "ask for membership expiry date" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))

      val result = await(controller.showAmlsDetailsForm(authenticatedRequest()))

      result should containMessages(
        "moneyLaunderingCompliance.expiry.title",
        "moneyLaunderingCompliance.expiry.hint",
        "moneyLaunderingCompliance.expiry.day.title",
        "moneyLaunderingCompliance.expiry.month.title",
        "moneyLaunderingCompliance.expiry.year.title"
      )
      result should containInputElement("expiry.day", "text")
      result should containInputElement("expiry.month", "text")
      result should containInputElement("expiry.year", "text")
    }

    "contain continue and save buttons" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))

      val result = await(controller.showAmlsDetailsForm(authenticatedRequest()))

      result should containSubmitButton(
        expectedMessageKey = "button.saveContinue",
        expectedElementId = "amls-details-continue"
      )

      result should containSubmitButton(
        expectedMessageKey = "button.saveComeBackLater",
        expectedElementId = "amls-details-save"
      )
    }

    "contain a form that would POST to /money-laundering-compliance" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))

      val result = await(controller.showAmlsDetailsForm(authenticatedRequest()))
      val doc = Jsoup.parse(bodyOf(result))

      val elForm = doc.select("form")
      elForm should not be null
      elForm.attr("action") shouldBe "/agent-subscription/money-laundering-compliance"
      elForm.attr("method") shouldBe "POST"
    }

    "pre-populate amls form if they are coming from /check_answers and also go to /check-money-laundering-compliance page when user clicks on 'Go Back' link" in
      new Setup {
      def minimalSubscriptionJourneyRecordWithAmls(authProviderId: AuthProviderId) =
        SubscriptionJourneyRecord(
          authProviderId,
          businessDetails = BusinessDetails(SoleTrader, validUtr, Postcode(validPostcode)),
          amlsData = Some(
            AmlsData(
              amlsRegistered = true,
              None,
              Some(AmlsDetails("Insolvency Practitioners Association (IPA)", Right(RegisteredDetails("123456789", Some(LocalDate.now()))))))))

      givenSubscriptionJourneyRecordExists(id, minimalSubscriptionJourneyRecordWithAmls(id))

        implicit val hc = HeaderCarrier()

      sessionStoreService.currentSession.goBackUrl = Some(routes.SubscriptionController.showCheckAnswers().url)

      val result = await(controller.showAmlsDetailsForm(authenticatedRequest()))

      contentAsString(result) should (include(
        """<a href="/agent-subscription/check-money-laundering-compliance" class="govuk-back-link">Back</a>""")
        and include("""selected id="amlsCode-IPA">Insolvency Practitioners Association (IPA)</option>""")
        and include("""value="123456789"""")
        and include(s"""value="${LocalDate.now().getYear.toString}""""))
    }
  }

  "submitAmlsDetailsForm (POST /money-laundering-compliance)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitAmlsDetailsForm(_))

    val expiryDate = LocalDate.now().plusDays(2)
    val expiryDay = expiryDate.getDayOfMonth.toString
    val expiryMonth = expiryDate.getMonthValue.toString
    val expiryYear = expiryDate.getYear.toString
    val amlsBodies = AMLSLoader.load("/amls.csv")

    "store AMLS form in temporary store after successful submission, and redirect to task list when change flag is false" in new Setup {
      val amlsBody = amlsBodies.getOrElse("AAT", throw new Exception("Invalid AMLS code"))
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenSubscriptionRecordCreated(
        id,
        record.copy(
          amlsData = Some(AmlsData.registeredUserNoDataEntered
            .copy(amlsDetails = Some(AmlsDetails(amlsBody, Right(RegisteredDetails("12345", Some(expiryDate)))))))))

      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "AAT",
        "membershipNumber" -> "12345",
        "expiry.day"       -> expiryDay,
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> expiryYear,
      "submit" -> "continue")

      sessionStoreService.currentSession.changingAnswers = Some(false)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.TaskListController.showTaskList().url
    }

    "store AMLS form in temporary store when the AMLS body is HMRC and the registration number is valid" in new Setup {
      val amlsBody = amlsBodies.getOrElse("HMRC", throw new Exception("Invalid AMLS code"))
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenAmlsRecordFound(validAmlsRegistrationNumber, Approved)
      givenSubscriptionRecordCreated(
        id,
        record.copy(
          amlsData = Some(AmlsData.registeredUserNoDataEntered
            .copy(amlsDetails = Some(AmlsDetails(amlsBody, Right(RegisteredDetails(validAmlsRegistrationNumber, Some(expiryDate)))))))))

      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "HMRC",
        "membershipNumber" -> validAmlsRegistrationNumber,
        "expiry.day"       -> expiryDay,
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> expiryYear,
        "submit" -> "continue")

      sessionStoreService.currentSession.changingAnswers = Some(false)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.TaskListController.showTaskList().url
    }

    "redirect to /money-laundering-compliance-not-found if the AMLS body is HMRC and the dates do not match" in new Setup {

      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenAmlsRecordFound(validAmlsRegistrationNumber, Approved)

      val wrongDate = LocalDate.now().plusDays(4)

      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "HMRC",
        "membershipNumber" -> validAmlsRegistrationNumber,
        "expiry.day"       -> s"${wrongDate.getDayOfMonth.toString}",
        "expiry.month"     -> s"${wrongDate.getMonthValue.toString}",
        "expiry.year"      -> s"${wrongDate.getYear.toString}",
        "submit" -> "continue")

      sessionStoreService.currentSession.changingAnswers = Some(false)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.AMLSController.showAmlsDetailsNotFound().url
    }

    "redirect to /money-laundering-compliance-not-found if the AMLS body is HMRC and the status is Rejected" in new Setup {

      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenAmlsRecordFound(validAmlsRegistrationNumber, Rejected)

      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "HMRC",
        "membershipNumber" -> validAmlsRegistrationNumber,
        "expiry.day"       -> expiryDay,
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> expiryYear,
        "submit" -> "continue")

      sessionStoreService.currentSession.changingAnswers = Some(false)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.AMLSController.showAmlsRecordIneligibleStatus().url
    }

    "redirect to /money-laundering-compliance-not-found if the AMLS body is HMRC and the registration number is not found" in new Setup {

      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenAmlsRecordNotFound(validAmlsRegistrationNumber)

      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "HMRC",
        "membershipNumber" -> validAmlsRegistrationNumber,
        "expiry.day"       -> expiryDay,
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> expiryYear,
        "submit" -> "continue")

      sessionStoreService.currentSession.changingAnswers = Some(false)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.AMLSController.showAmlsDetailsNotFound().url
    }

    "redirect to /money-laundering-compliance-suspended if the AMLS body is HMRC and the registration record is marked as suspended" in new Setup {

      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenAmlsRecordFound(validAmlsRegistrationNumber, Approved, Some(true))

      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "HMRC",
        "membershipNumber" -> validAmlsRegistrationNumber,
        "expiry.day"       -> expiryDay,
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> expiryYear,
        "submit" -> "continue")

      sessionStoreService.currentSession.changingAnswers = Some(false)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.AMLSController.showAmlsRecordIneligibleStatus().url
    }

    "store AMLS form in temporary store after successful submission, and redirect to change answers when change flag is true" in new Setup {
      val amlsBody: String = amlsBodies.getOrElse("AAT", throw new Exception("Invalid AMLS code"))
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenSubscriptionRecordCreated(
        id,
        record.copy(
          amlsData = Some(AmlsData.registeredUserNoDataEntered
            .copy(amlsDetails = Some(AmlsDetails(amlsBody, Right(RegisteredDetails("12345", Some(expiryDate)))))))))

      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "AAT",
        "membershipNumber" -> "12345",
        "expiry.day"       -> expiryDay,
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> expiryYear,
      "submit" -> "continue")

      sessionStoreService.currentSession.changingAnswers = Some(true)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.SubscriptionController.showCheckAnswers().url
    }

    "show validation error when the form is submitted with empty amlsCode" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "",
        "membershipNumber" -> "12345",
        "expiry.day"       -> expiryDay,
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages(
        "moneyLaunderingCompliance.amls.title",
        "error.moneyLaunderingCompliance.amlscode.empty")
    }

    "show validation error when the form is submitted with invalid amlsCode" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "Invalid Text",
        "membershipNumber" -> "12345",
        "expiry.day"       -> expiryDay,
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages(
        "moneyLaunderingCompliance.amls.title",
        "error.moneyLaunderingCompliance.amlscode.invalid")
    }

    "show validation error when the form is submitted with empty membership number" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "AAT",
        "membershipNumber" -> "",
        "expiry.day"       -> expiryDay,
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages(
        "moneyLaunderingCompliance.membershipNumber.title",
        "error.moneyLaunderingCompliance.membershipNumber.empty")
    }

    "show validation error when the form is submitted with invalid expiry date" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "AAT",
        "membershipNumber" -> "12345",
        "expiry.day"       -> "123",
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages(
        "moneyLaunderingCompliance.expiry.title",
        "error.moneyLaunderingCompliance.date.invalid")
    }

    "show validation error when the form is submitted with empty day field" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "AAT",
        "membershipNumber" -> "12345",
        "expiry.day"       -> "",
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages(
        "moneyLaunderingCompliance.expiry.title",
        "error.moneyLaunderingCompliance.day.empty")
    }

    "show validation error when the form is submitted with empty month field" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "AAT",
        "membershipNumber" -> "12345",
        "expiry.day"       -> expiryDay,
        "expiry.month"     -> "",
        "expiry.year"      -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages(
        "moneyLaunderingCompliance.expiry.title",
        "error.moneyLaunderingCompliance.month.empty")
    }

    "show validation error when the form is submitted with empty year field" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "AAT",
        "membershipNumber" -> "12345",
        "expiry.day"       -> expiryDay,
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> "")

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages(
        "moneyLaunderingCompliance.expiry.title",
        "error.moneyLaunderingCompliance.year.empty")
    }

    "show validation error when the form is submitted with empty day and month field" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "AAT",
        "membershipNumber" -> "12345",
        "expiry.day"       -> "",
        "expiry.month"     -> "",
        "expiry.year"      -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages(
        "moneyLaunderingCompliance.expiry.title",
        "error.moneyLaunderingCompliance.day.month.empty")
      result shouldNot containMessages(
        "error.moneyLaunderingCompliance.day.empty",
        "error.moneyLaunderingCompliance.month.empty")
    }

    "show validation error when the form is submitted with empty day and year field" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "AAT",
        "membershipNumber" -> "12345",
        "expiry.day"       -> "",
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> "")

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages(
        "moneyLaunderingCompliance.expiry.title",
        "error.moneyLaunderingCompliance.day.year.empty")
      result shouldNot containMessages(
        "error.moneyLaunderingCompliance.day.empty",
        "error.moneyLaunderingCompliance.year.empty")
    }

    "show validation error when the form is submitted with empty month and year field" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "AAT",
        "membershipNumber" -> "12345",
        "expiry.day"       -> expiryDay,
        "expiry.month"     -> "",
        "expiry.year"      -> "")

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages(
        "moneyLaunderingCompliance.expiry.title",
        "error.moneyLaunderingCompliance.month.year.empty")
      result shouldNot containMessages(
        "error.moneyLaunderingCompliance.month.empty",
        "error.moneyLaunderingCompliance.year.empty")
    }

    "show validation error when the form is submitted with HMRC as AMLS body and the membership number fails the HMRC regex" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"         -> "HMRC",
        "membershipNumber" -> "12345",
        "expiry.day"       -> expiryDay,
        "expiry.month"     -> expiryMonth,
        "expiry.year"      -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages("error.moneyLaunderingCompliance.membershipNumber.invalid")
    }
  }

  "GET /money-laundering-compliance-incomplete" should {

    "display page with correct content" in new Setup {

      val result = await(controller.showAmlsNotAppliedPage(authenticatedRequest()))

      result should containMessages(
        "amls-not-applied.title",
        "amls-not-applied.p1",
        "link.finishSignOut"
      )

      result should containSubstrings(
        "To find details of supervisory bodies, see",
        "anti-money laundering registration (opens in a new tab).")
    }
  }

  "GET  /money-laundering-application-details" should {

    behave like anAgentAffinityGroupOnlyEndpoint(controller.showAmlsApplicationDatePage(_))

    "display page with correct content" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))

      val result = await(controller.showAmlsApplicationDatePage(authenticatedRequest()))

      result should containMessages(
        "amls.pending.appliedOn.title",
        "amls.pending.appliedOn.title"
      )

      result should containSubmitButton("button.saveContinue","amls-pending-continue")
      result should containSubmitButton("button.saveComeBackLater","amls-pending-save")
    }

    "display and pre-populate page when this information is in the store" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecord(id).copy(amlsData =
        Some(AmlsData(false, Some(true), Some(AmlsDetails("supervisory", Left(PendingDetails(Some(LocalDate.now().minusDays(5))))))))))

      val result = await(controller.showAmlsApplicationDatePage(authenticatedRequest()))

      result should containMessages(
        "amls.pending.appliedOn.title",
        "amls.pending.appliedOn.title"
      )

      result should containSubmitButton("button.saveContinue","amls-pending-continue")
      result should containSubmitButton("button.saveComeBackLater","amls-pending-save")
    }
  }

  "POST /money-laundering-application-details" should {

    val appliedOnDate = LocalDate.now().minusMonths(1)
    val day = appliedOnDate.getDayOfMonth.toString
    val month = appliedOnDate.getMonthValue.toString
    val year = appliedOnDate.getYear.toString

    "store AMLS pending details in temporary store after successful submission, redirect to task list when change flag is false" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenSubscriptionRecordCreated(
        id,
        record.copy(
          amlsData = Some(
            AmlsData.registeredUserNoDataEntered.copy(
              amlsDetails = Some(AmlsDetails("Association of AccountingTechnicians (AAT)", Left(PendingDetails(Some(appliedOnDate))))))))
      )
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"        -> "AAT",
        "appliedOn.day"   -> day,
        "appliedOn.month" -> month,
        "appliedOn.year"  -> year,
      "submit" -> "continue")

      sessionStoreService.currentSession.changingAnswers = Some(false)

      val result = await(controller.submitAmlsApplicationDatePage(request))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.TaskListController.showTaskList().url
    }

    "store AMLS pending details in temporary store after successful submission, redirect to check answers when change flag is true" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenSubscriptionRecordCreated(
        id,
        record.copy(
          amlsData = Some(
            AmlsData.registeredUserNoDataEntered.copy(
              amlsDetails = Some(AmlsDetails("Association of AccountingTechnicians (AAT)", Left(PendingDetails(Some(appliedOnDate))))))))
      )
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"        -> "AAT",
        "appliedOn.day"   -> day,
        "appliedOn.month" -> month,
        "appliedOn.year"  -> year,
      "submit" -> "continue")

      sessionStoreService.currentSession.changingAnswers = Some(true)

      val result = await(controller.submitAmlsApplicationDatePage(request))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.SubscriptionController.showCheckAnswers().url
    }

    "show validation error when the form is submitted with empty day field" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"        -> "AAT",
        "appliedOn.day"   -> "",
        "appliedOn.month" -> month,
        "appliedOn.year"  -> year)

      val result = await(controller.submitAmlsApplicationDatePage(request))
      status(result) shouldBe 200
      result should containMessages("amls.pending.appliedOn.title", "error.amls.pending.appliedOn.day.empty")
    }

    "show validation error when the form is submitted with invalid appliedOn date" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"        -> "AAT",
        "appliedOn.day"   -> "123",
        "appliedOn.month" -> month,
        "appliedOn.year"  -> year)

      val result = await(controller.submitAmlsApplicationDatePage(request))
      status(result) shouldBe 200
      result should containMessages("error.moneyLaunderingCompliance.date.invalid")
    }

    "show validation error when the form is submitted with empty month field" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"        -> "AAT",
        "appliedOn.day"   -> day,
        "appliedOn.month" -> "",
        "appliedOn.year"  -> year)

      val result = await(controller.submitAmlsApplicationDatePage(request))
      status(result) shouldBe 200
      result should containMessages("error.amls.pending.appliedOn.month.empty")
    }

    "show validation error when the form is submitted with empty year field" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"        -> "AAT",
        "appliedOn.day"   -> day,
        "appliedOn.month" -> month,
        "appliedOn.year"  -> "")

      val result = await(controller.submitAmlsApplicationDatePage(request))
      status(result) shouldBe 200
      result should containMessages("error.amls.pending.appliedOn.year.empty")
    }

    "show validation error when the form is submitted with empty day and month field" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"        -> "AAT",
        "appliedOn.day"   -> "",
        "appliedOn.month" -> "",
        "appliedOn.year"  -> year)

      val result = await(controller.submitAmlsApplicationDatePage(request))
      status(result) shouldBe 200
      result should containMessages("error.amls.pending.appliedOn.day.month.empty")
      result shouldNot containMessages(
        "error.amls.pending.appliedOn.day.empty",
        "error.amls.pending.appliedOn.month.empty")
    }

    "show validation error when the form is submitted with empty day and year field" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"        -> "AAT",
        "appliedOn.day"   -> "",
        "appliedOn.month" -> month,
        "appliedOn.year"  -> "")

      val result = await(controller.submitAmlsApplicationDatePage(request))
      status(result) shouldBe 200
      result should containMessages("error.amls.pending.appliedOn.day.year.empty")
      result shouldNot containMessages(
        "error.amls.pending.appliedOn.day.empty",
        "error.amls.pending.appliedOn.year.empty")
    }

    "show validation error when the form is submitted with empty month and year field" in new Setup {
      implicit val request = authenticatedRequest(POST).withFormUrlEncodedBody(
        "amlsCode"        -> "AAT",
        "appliedOn.day"   -> day,
        "appliedOn.month" -> "",
        "appliedOn.year"  -> "")

      val result = await(controller.submitAmlsApplicationDatePage(request))
      status(result) shouldBe 200
      result should containMessages("error.amls.pending.appliedOn.month.year.empty")
      result shouldNot containMessages(
        "error.amls.pending.appliedOn.month.empty",
        "error.amls.pending.appliedOn.year.empty")
    }

  }

  "GET /money-laundering-details-not-found" should {
    "display the correct content" in new Setup {
      val result = await(controller.showAmlsDetailsNotFound(authenticatedRequest()))

      result should containMessages(
        "amls-details-not-found.title",
        "amls-details-not-found.p"
      )

      result should containLink("amls-details-not-found.button", routes.AMLSController.showAmlsDetailsForm().url)
    }
  }

  "GET /money-laundering-not-eligible" should {
    "display the correct content" in new Setup {
      val result = await(controller.showAmlsRecordIneligibleStatus(authenticatedRequest()))

      result should containMessages(
        "amls-ineligible-status.title",
        "amls-ineligible-status.p1",
        "amls-ineligible-status.li.1",
        "amls-ineligible-status.li.2",
        "amls-ineligible-status.li.3",
      )
      result should containLink("amls-ineligible-status.link", appConfig.amlsGuidanceLink)
    }
  }

}
