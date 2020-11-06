package uk.gov.hmrc.agentsubscriptionfrontend.controllers
import java.time.LocalDate

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AmlsDetails, AuthProviderId, ContactEmailData, ContactTradingAddressData, ContactTradingNameData, PendingDetails, RegisteredDetails}
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.AmlsData
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.{givenNoSubscriptionJourneyRecordExists, givenSubscriptionJourneyRecordExists}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{subscribingAgentEnrolledForHMRCASAGENT, subscribingAgentEnrolledForNonMTD}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestData}
import play.api.test.Helpers._

class TaskListControllerISpec extends BaseISpec {
  lazy val controller: TaskListController = app.injector.instanceOf[TaskListController]
  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  "showTaskList (GET /task-list)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showTaskList(_))

    "contain page titles and header content when the user is subscribing" in {
      givenAgentIsNotManuallyAssured(validUtr.value)

      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData.minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId")))

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
      "task-list.checkAnswersSubTask")
    }

    "contain CONTINUE tag when a task has been completed" in {

      givenAgentIsNotManuallyAssured(validUtr.value)

      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData
          .minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(amlsData = Some(AmlsData(
            amlsRegistered = true,
            amlsAppliedFor = Some(false),
            amlsDetails =
              Some(AmlsDetails("supervisory body", Right(RegisteredDetails("123", LocalDate.now().plusDays(10)))))
          )))
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      result should containMessages("task-list.header", "task-list.completed")
    }

    "contain a CONTINUE tag when amls task has been completed and allow agent to re-click link when they are not manually assured" in {

      givenAgentIsNotManuallyAssured(validUtr.value)

      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData
          .minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(
            amlsData = Some(
              AmlsData(
                amlsRegistered = true,
                amlsAppliedFor = Some(false),
                amlsDetails =
                  Some(AmlsDetails("supervisory body", Right(RegisteredDetails("123", LocalDate.now().plusDays(10)))))
              )))
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      result should containMessages("task-list.header", "task-list.amlsTask.header", "task-list.amlsSubTask", "task-list.completed")

      result should containLink("task-list.amlsSubTask", routes.AMLSController.showAmlsRegisteredPage().url)
    }

    "contain a url to the contact details email check task when user has completed amls" in {
      givenAgentIsNotManuallyAssured(validUtr.value)
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData.minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(amlsData = Some(AmlsData(amlsRegistered = true, None,
            Some(AmlsDetails("supervisory", Left(PendingDetails(LocalDate.now().minusDays(20))))))), continueId = Some("continue-id")))

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      status(result) shouldBe 200

      result should containLink("task-list.contactDetailsEmailSubTask", routes.ContactDetailsController.showContactEmailCheck().url)
    }

    "contain a url to the contact details trading name sub-task when user has completed email-subtask" in {
      givenAgentIsNotManuallyAssured(validUtr.value)
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData.minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(amlsData = Some(AmlsData(amlsRegistered = true, None,
            Some(AmlsDetails("supervisory", Left(PendingDetails(LocalDate.now().minusDays(20))))))), continueId = Some("continue-id"),
            contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))))
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      status(result) shouldBe 200

      result should containLink("task-list.contactDetailsTradingNameSubTask", routes.ContactDetailsController.showTradingNameCheck().url)
    }

    "contain a url to the contact details trading address sub-task when user has completed trading-name-subtask" in {
      givenAgentIsNotManuallyAssured(validUtr.value)
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData.minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(amlsData = Some(AmlsData(amlsRegistered = true, None,
            Some(AmlsDetails("supervisory", Left(PendingDetails(LocalDate.now().minusDays(20))))))), continueId = Some("continue-id"),
            contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
            contactTradingNameData = Some(ContactTradingNameData(true, Some("My Trading Name"))))
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      status(result) shouldBe 200

      result should containLink("task-list.contactDetailsTradingNameSubTask", routes.ContactDetailsController.showTradingNameCheck().url)
      result should containLink("task-list.contactDetailsTradingAddressSubTask", routes.ContactDetailsController.showCheckMainTradingAddress().url)
    }

    "contain a url to the mapping journey when user has completed contact details" in {
      givenAgentIsNotManuallyAssured(validUtr.value)
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData.minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(amlsData = Some(AmlsData(amlsRegistered = true, None,
            Some(AmlsDetails("supervisory", Left(PendingDetails(LocalDate.now().minusDays(20))))))), continueId = Some("continue-id"),
            contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
            contactTradingNameData = Some(ContactTradingNameData(true, Some(tradingName))),
            contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress)))))

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      status(result) shouldBe 200

      checkHtmlResultWithBodyText(result, "/agent-mapping/task-list/start?continueId=continue-id")
    }

    "redirect to business type if there is no record for this agents auth provider id" in {
      givenAgentIsNotManuallyAssured(validUtr.value)
      givenNoSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showTaskList(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }

  "savedProgress (GET /saved-progress)" should {
    "contain page title and content" in {

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      val result = await(controller.savedProgress(backLink = None)(request))

      status(result) shouldBe 200

      result should containMessages(
        "saved-progress.title",
        "saved-progress.p1",
        "saved-progress.p2",
        "saved-progress.link",
        "saved-progress.continue"
      )

      result should containSubstrings(
        "To complete this form later, go to the",
        "guidance page about creating an agent services account (opens in a new tab)",
        "on GOV.UK and sign in to this service again."
      )

      result should containLink("saved-progress.continue", routes.TaskListController.showTaskList().url)
      result should containLink("saved-progress.finish", routes.SignedOutController.startSurvey().url)
    }
  }
}
