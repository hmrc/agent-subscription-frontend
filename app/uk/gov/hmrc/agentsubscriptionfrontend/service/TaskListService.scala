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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.{AgentAssuranceConnector, AgentSubscriptionConnector}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, SubscriptionJourneyRecord}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaskListService @Inject() (
  agentAssuranceConnector: AgentAssuranceConnector,
  agentSubscriptionConnector: AgentSubscriptionConnector,
  appConfig: AppConfig
) {

  def createTasks(
    subscriptionJourneyRecord: SubscriptionJourneyRecord
  )(implicit rh: RequestHeader, ec: ExecutionContext): Future[List[Task]] =
    for {
      amlsOpt <- agentAssuranceConnector.getAmlsData(subscriptionJourneyRecord.businessDetails.utr)
    } yield {
      val journeyRecord = (subscriptionJourneyRecord.amlsData, amlsOpt) match {
        case (None, Some(amls)) =>
          val newJourneyRecord = subscriptionJourneyRecord
            .copy(amlsData = Some(AmlsData(amlsRegistered = amls.isRegistered, Some(amls.isPending), amlsOpt)))
          agentSubscriptionConnector.createOrUpdateJourney(newJourneyRecord)
          newJourneyRecord
        case _ => subscriptionJourneyRecord
      }
      if (isCleanCredsAgent(journeyRecord)) {
        val amlsAndContactDetailsTaskList: List[Task] = amlsAndContactDetailsTasks(journeyRecord)
        val checkAnswersTask: Task = CheckAnswersTask(List(CheckAnswersSubTask(amlsAndContactDetailsTaskList.forall(_.isComplete))))
        amlsAndContactDetailsTaskList ::: List(checkAnswersTask)

      } else {

        val amlsAndContactDetailsTaskList: List[Task] = amlsAndContactDetailsTasks(journeyRecord)

        val mappingTask: Task = MappingTask(
          List(
            MappingSubTask(
              journeyRecord.cleanCredsAuthProviderId,
              journeyRecord.mappingComplete,
              journeyRecord.continueId.getOrElse(" "),
              amlsAndContactDetailsTaskList.forall(_.isComplete),
              appConfig
            )
          )
        )
        val createIDTask: Task = CreateIDTask(List(CreateIDSubTask(journeyRecord.cleanCredsAuthProviderId, mappingTask.isComplete)))
        val checkAnswersTask: Task = CheckAnswersTask(List(CheckAnswersSubTask(createIDTask.isComplete)))
        amlsAndContactDetailsTaskList ::: List(mappingTask, createIDTask, checkAnswersTask)
      }
    }

  def isCleanCredsAgent(subscriptionJourneyRecord: SubscriptionJourneyRecord): Boolean =
    subscriptionJourneyRecord.cleanCredsAuthProviderId.contains(subscriptionJourneyRecord.authProviderId)

  private def amlsAndContactDetailsTasks(subscriptionJourneyRecord: SubscriptionJourneyRecord): List[Task] = {

    val amlsTask: Task = AmlsTask(List(AmlsSubTask(subscriptionJourneyRecord.amlsData)))

    val contactEmailSubTask: SubTask = ContactDetailsEmailSubTask(
      subscriptionJourneyRecord.contactEmailData,
      amlsTask.isComplete
    )
    val contactTradingNameSubTask: SubTask = ContactTradingNameSubTask(
      subscriptionJourneyRecord.contactTradingNameData,
      contactEmailSubTask.isComplete
    )
    val contactTradingAddressSubTask: SubTask = ContactTradingAddressSubTask(
      subscriptionJourneyRecord.contactTradingAddressData,
      contactTradingNameSubTask.isComplete
    )
    val contactTelephoneNumberSubTask: SubTask = ContactTelephoneNumberSubTask(
      subscriptionJourneyRecord.contactTelephoneData,
      contactTradingAddressSubTask.isComplete
    )
    val contactDetailsTask: Task = ContactDetailsTask(
      List(
        contactEmailSubTask,
        contactTradingNameSubTask,
        contactTradingAddressSubTask,
        contactTelephoneNumberSubTask
      )
    )
    List(amlsTask, contactDetailsTask)
  }

}
