package uk.gov.hmrc.agentsubscriptionfrontend.controllers
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub.userIsAuthenticated
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{subscribingAgentEnrolledForNonMTD, subscribingCleanAgentWithoutEnrolments}

class TaskListControllerSpec extends BaseISpec {
  lazy val controller: TaskListController = app.injector.instanceOf[TaskListController]

  "showTaskList (GET /task-list)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showTaskList(_))

    behave like aPageTakingContinueUrlAndCachingInSessionStore(
      controller.showTaskList(_),
      userIsAuthenticated(subscribingCleanAgentWithoutEnrolments))

    "contain page titles and header content" in {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showTaskList(request))

      result should containMessages(
        "task-list.header",
        "task-list.1.header",
        "task-list.2.header",
        "task-list.3.header",
        "task-list.4.header")
    }
  }
}
