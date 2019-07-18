package uk.gov.hmrc.agentsubscriptionfrontend.service

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, TaskListFlags}
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaskListService @Inject() (agentAssuranceConnector: AgentAssuranceConnector) {

  def getTaskListFlags(subscriptionJourneyRecord: SubscriptionJourneyRecord)
                      (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TaskListFlags] = {
    for {
      manuallyAssured <- isMaaAgent(subscriptionJourneyRecord.businessDetails.utr)
    } yield TaskListFlags(
      businessTaskComplete = true,
      amlsTaskComplete = isAmlsTaskComplete(subscriptionJourneyRecord),
      isMAA = manuallyAssured,
      createTaskComplete = isCreateTaskComplete(subscriptionJourneyRecord),
      checkAnswersComplete = isCheckAnswersComplete(subscriptionJourneyRecord)
    )
  }

  private def isMaaAgent(utr: Utr)(implicit hc: HeaderCarrier): Future[Boolean] = agentAssuranceConnector.isManuallyAssuredAgent(utr)

  private def isAmlsTaskComplete(subscriptionJourneyRecord: SubscriptionJourneyRecord): Boolean = {
    subscriptionJourneyRecord.amlsData.fold(false)(_ => true)
  }

  private def isCreateTaskComplete(subscriptionJourneyRecord: SubscriptionJourneyRecord): Boolean = {
    subscriptionJourneyRecord.cleanCredsInternalId.fold(false)(_ => true)
  }

  private def isCheckAnswersComplete(subscriptionJourneyRecord: SubscriptionJourneyRecord): Boolean = ???

}
