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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers
import org.jsoup.Jsoup
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.AmlsData
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.{givenNoSubscriptionJourneyRecordExists, givenSubscriptionJourneyRecordExists}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, Css, TestData}

import java.time.LocalDate
import scala.concurrent.Future

class TaskListControllerISpecIt extends BaseISpecIt with EmailVerificationBehaviours {
  lazy val controller: TaskListController = app.injector.instanceOf[TaskListController]
  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  val twentyDaysAgo: LocalDate = LocalDate.now().minusDays(20)
  val twentyDaysFromNow: LocalDate = LocalDate.now().plusDays(20)

  val pendingAmlsDetails: AmlsDetails =
    AmlsDetails("supervisory", membershipNumber = Some("12345"), appliedOn = Some(twentyDaysAgo), membershipExpiresOn = None)
  val registeredAmlsDetails: AmlsDetails =
    AmlsDetails("supervisory", membershipNumber = Some("12345"), appliedOn = None, membershipExpiresOn = Some(twentyDaysFromNow))

  "showTaskList (GET /task-list)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showTaskList(_))

    "contain page titles and header content when the user is subscribing" in {
      givenAgentIsNotManuallyAssured(validUtr)
      givenAmlsDataIsNotFound(validUtr)

      givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"), TestData.minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId")))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showTaskList(request))

      result should containMessages(
        "task-list.header",
        "task-list.subheader",
        "task-list.1.number",
        "task-list.amlsTask.header",
        "task-list.amlsSubTask",
        "task-list.2.number",
        "task-list.contactDetailsTask.header",
        "task-list.contactDetailsEmailSubTask",
        "task-list.contactDetailsTradingNameSubTask",
        "task-list.contactDetailsTradingAddressSubTask",
        "task-list.3.number",
        "task-list.mappingTask.header",
        "task-list.mappingSubTask",
        "task-list.4.number",
        "task-list.createIDTask.header",
        "task-list.createIDSubTask",
        "task-list.5.number",
        "task-list.checkAnswersTask.header",
        "task-list.checkAnswersSubTask"
      )
    }

    "contain CONTINUE tag when a task has been completed" in {

      givenAgentIsNotManuallyAssured(validUtr)
      givenAmlsDataIsNotFound(validUtr)

      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData
          .minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(amlsData =
            Some(
              AmlsData(
                amlsRegistered = true,
                amlsAppliedFor = Some(false),
                amlsDetails = Some(registeredAmlsDetails)
              )
            )
          )
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      result should containMessages("task-list.header", "task-list.completed")
    }

    "contain a CONTINUE tag when amls task has been completed and allow agent to re-click link when they are not manually assured" in {

      givenAgentIsNotManuallyAssured(validUtr)
      givenAmlsDataIsNotFound(validUtr)

      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData
          .minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(
            amlsData = Some(
              AmlsData(
                amlsRegistered = true,
                amlsAppliedFor = Some(false),
                amlsDetails = Some(registeredAmlsDetails)
              )
            )
          )
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      result should containMessages("task-list.header", "task-list.amlsTask.header", "task-list.amlsSubTask", "task-list.completed")

      result should containLink("task-list.amlsSubTask", routes.AMLSController.showAmlsRegisteredPage().url)
    }

    "contain a url to the contact details email check task when user has completed amls (pending details)" in {
      givenAgentIsNotManuallyAssured(validUtr)
      givenAmlsDataIsNotFound(validUtr)
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData
          .minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(amlsData = Some(AmlsData(amlsRegistered = false, Some(true), Some(pendingAmlsDetails))), continueId = Some("continue-id"))
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      status(result) shouldBe 200

      result should containLink("task-list.contactDetailsEmailSubTask", routes.ContactDetailsController.showContactEmailCheck().url)
    }

    "contain a url to the contact details email check task when user has completed amls (registered details)" in {
      givenAgentIsNotManuallyAssured(validUtr)
      givenAmlsDataIsNotFound(validUtr)
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData
          .minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(amlsData = Some(AmlsData(amlsRegistered = true, None, Some(registeredAmlsDetails))), continueId = Some("continue-id"))
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      status(result) shouldBe 200

      result should containLink("task-list.contactDetailsEmailSubTask", routes.ContactDetailsController.showContactEmailCheck().url)
    }

    "contain a url to the contact details trading name sub-task when user has completed email-subtask" in {
      givenAgentIsNotManuallyAssured(validUtr)
      givenAmlsDataIsNotFound(validUtr)
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData
          .minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(
            amlsData = Some(AmlsData(amlsRegistered = false, Some(true), Some(pendingAmlsDetails))),
            continueId = Some("continue-id"),
            contactEmailData = Some(ContactEmailData(useBusinessEmail = true, Some("email@email.com")))
          )
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      status(result) shouldBe 200

      result should containLink("task-list.contactDetailsTradingNameSubTask", routes.ContactDetailsController.showTradingNameCheck().url)
    }

    "contain a url to the contact details trading address sub-task when user has completed trading-name-subtask" in {
      givenAgentIsNotManuallyAssured(validUtr)
      givenAmlsDataIsNotFound(validUtr)
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData
          .minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(
            amlsData = Some(AmlsData(amlsRegistered = false, Some(true), Some(pendingAmlsDetails))),
            continueId = Some("continue-id"),
            contactEmailData = Some(ContactEmailData(useBusinessEmail = true, Some("email@email.com"))),
            contactTradingNameData = Some(ContactTradingNameData(hasTradingName = true, Some("My Trading Name")))
          )
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      status(result) shouldBe 200

      result should containLink("task-list.contactDetailsTradingNameSubTask", routes.ContactDetailsController.showTradingNameCheck().url)
      result should containLink("task-list.contactDetailsTradingAddressSubTask", routes.ContactDetailsController.showCheckMainTradingAddress().url)
    }

    "contain a url to the contact details telephone number when user has completed trading name sub-task" in {
      givenAgentIsNotManuallyAssured(validUtr)
      givenAmlsDataIsNotFound(validUtr)
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData
          .minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(
            amlsData = Some(AmlsData(amlsRegistered = false, Some(true), Some(pendingAmlsDetails))),
            continueId = Some("continue-id"),
            contactEmailData = Some(ContactEmailData(useBusinessEmail = true, Some("email@email.com"))),
            contactTradingNameData = Some(ContactTradingNameData(hasTradingName = true, Some(tradingName))),
            contactTradingAddressData = Some(ContactTradingAddressData(useBusinessAddress = true, Some(businessAddress)))
          )
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      status(result) shouldBe 200

      result should containLink("task-list.contactDetailsTelephoneNumberSubTask", routes.ContactDetailsController.contactPhoneCheck.url)

      // checkHtmlResultWithBodyText(result, "/agent-mapping/task-list/start?continueId=continue-id")
    }

    "contain a url to the mapping journey when user has completed telephone number sub-task" in {
      givenAgentIsNotManuallyAssured(validUtr)
      givenAmlsDataIsNotFound(validUtr)
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData
          .minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(
            amlsData = Some(AmlsData(amlsRegistered = false, Some(true), Some(pendingAmlsDetails))),
            continueId = Some("continue-id"),
            contactEmailData = Some(ContactEmailData(useBusinessEmail = true, Some("email@email.com"))),
            contactTradingNameData = Some(ContactTradingNameData(hasTradingName = true, Some(tradingName))),
            contactTradingAddressData = Some(ContactTradingAddressData(useBusinessAddress = true, Some(businessAddress))),
            contactTelephoneData = Some(ContactTelephoneData(useBusinessTelephone = true, Some(phoneNumber)))
          )
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      status(result) shouldBe 200

      checkHtmlResultWithBodyText(result, "/agent-mapping/task-list/start?continueId=continue-id")
    }

    "redirect to business type if there is no record for this agents auth provider id" in {
      givenAgentIsNotManuallyAssured(validUtr)
      givenAmlsDataIsNotFound(validUtr)
      givenNoSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showTaskList(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }

    behave like checksIfEmailIsVerified(TestData.couldBePartiallySubscribedJourneyRecord, isExpectedResult = status(_) == 200) { () =>
      givenAgentIsNotManuallyAssured(utr)
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      controller.showTaskList(request)
    }
  }

  "savedProgress (GET /saved-progress)" should {
    "contain page title and content" in {

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      val result = await(controller.savedProgress(backLink = None)(request))

      status(result) shouldBe 200

      val html = Jsoup.parse(Helpers.contentAsString(Future.successful(result)))
      html.title() shouldBe "Your progress has been saved for 30 days - Create an agent services account - GOV.UK"
      html.select(Css.H2).text() shouldBe "What you need to do next"
      val paragraphs = html.select(Css.paragraphs)
      paragraphs.get(0).text() shouldBe "You need to come back and complete this form within 30 days."
      paragraphs.get(1).text() shouldBe "To complete this form later, go to the guidance page about " +
        "creating an agent services account (opens in a new tab) on GOV.UK and sign in to this service again."
      paragraphs
        .get(2)
        .text() shouldBe "You will need to sign in with the same Government Gateway user ID you used when you started filling out this form."

      html.select("a#finish-signout").text() shouldBe "Finish and sign out"
      html.select("a#finish-signout").attr("href") shouldBe routes.SignOutController.startSurvey().url
      html.select("a#continue-saved-progress").text() shouldBe "Continue where you left off"
      html.select("a#continue-saved-progress").attr("href") shouldBe routes.TaskListController.showTaskList().url

    }
  }
}
