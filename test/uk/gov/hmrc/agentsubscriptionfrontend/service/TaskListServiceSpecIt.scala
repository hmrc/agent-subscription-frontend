/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any

import java.time.LocalDate
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.{AgentAssuranceConnector, AgentSubscriptionConnector}
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney._
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.agentsubscriptionfrontend.support.UnitSpecIt

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class TaskListServiceSpecIt extends UnitSpecIt with MockitoSugar {

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val ec: ExecutionContextExecutor = ExecutionContext.global

  private val stubAssuranceConnector = mock[AgentAssuranceConnector]
  private val stubAgentSubscriptionConnector = mock[AgentSubscriptionConnector]

  private val stubAppConfig = mock[AppConfig]

  private def givenAmlsDataNotPresent: OngoingStubbing[Future[Option[AmlsDetails]]] =
    when(stubAssuranceConnector.getAmlsData(Utr("12345")))
      .thenReturn(Future.successful(None))

  private def givenAmlsDataIsPresent: OngoingStubbing[Future[Option[AmlsDetails]]] =
    when(stubAssuranceConnector.getAmlsData(Utr("12345")))
      .thenReturn(Future.successful(Some(AmlsDetails("HMRC", Left(PendingDetails(Some(LocalDate.now())))))))

  private val taskListService = new TaskListService(stubAssuranceConnector, stubAgentSubscriptionConnector, stubAppConfig)

  val minimalUncleanCredsRecord = SubscriptionJourneyRecord(
    AuthProviderId("cred-1234"),
    None,
    BusinessDetails(BusinessType.LimitedCompany, Utr("12345"), Postcode("BN25GJ"), None, None, None, None, None, None),
    None,
    List.empty,
    mappingComplete = false,
    None,
    None
  )

  val minimalCleanCredsRecord = SubscriptionJourneyRecord(
    AuthProviderId("cred-1234"),
    None,
    BusinessDetails(BusinessType.LimitedCompany, Utr("12345"), Postcode("BN25GJ"), None, None, None, None, None, None),
    None,
    List.empty,
    mappingComplete = false,
    Some(AuthProviderId("cred-1234")),
    None
  )

  val tradingName = "My Trading Name"

  val tradingAddress =
    BusinessAddress("TradingAddress1 A", Some("TradingAddress2 A"), Some("TradingAddress3 A"), Some("TradingAddress4 A"), Some("TT11TT"), "GB")

  "when the user has unclean creds show the full task list" should {
    "AmlsTask" should {
      "when agent doesn't has amls data show AMLS link" in {
        givenAmlsDataNotPresent
        val tasks = await(taskListService.createTasks(minimalUncleanCredsRecord))

        tasks.length shouldBe 5
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.subTasks.head.showLink shouldBe true
      }

      "when agent has registered AMLS data with no renewal date show AMLS as not complete" in {
        givenAmlsDataNotPresent
        val amlsRecord = minimalUncleanCredsRecord.copy(
          amlsData = Some(
            AmlsData(amlsRegistered = true, None, Some(AmlsDetails("supervisory", Right(RegisteredDetails("123", None)))))))
        val tasks = await(taskListService.createTasks(amlsRecord))

        tasks.length shouldBe 5
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.isComplete shouldBe false
      }

      "when agent has registered AMLS data with renewal date nonempty show AMLS as complete" in {
        givenAmlsDataNotPresent
        val amlsRecord = minimalUncleanCredsRecord.copy(
          amlsData = Some(
            AmlsData(amlsRegistered = true, None, Some(AmlsDetails("supervisory", Right(RegisteredDetails("123", Some(LocalDate.now().plusDays(20)))))))))
        val tasks = await(taskListService.createTasks(amlsRecord))

        tasks.length shouldBe 5
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.isComplete shouldBe true
      }

      "when agent has pending AMLS data show AMLS as complete" in {
        givenAmlsDataNotPresent
        val amlsRecord = minimalUncleanCredsRecord.copy(
          amlsData =
            Some(AmlsData(amlsRegistered = false, Some(true), Some(AmlsDetails("supervisory", Left(PendingDetails(Some(LocalDate.now().minusDays(20)))))))))
        val tasks = await(taskListService.createTasks(amlsRecord))

        tasks.length shouldBe 5
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.isComplete shouldBe true
      }

      "when agent is has amls data already stored show AMLS as complete" in {
        givenAmlsDataIsPresent
        when(stubAgentSubscriptionConnector.createOrUpdateJourney(any[SubscriptionJourneyRecord])(any[HeaderCarrier]))
          .thenReturn(Future.successful(1))
        val tasks = await(taskListService.createTasks(minimalUncleanCredsRecord))
        tasks.length shouldBe 5
        tasks.head.isComplete shouldBe true
      }

      "when agent has partially completed AMLS details show AMLS as not complete" in {
        givenAmlsDataNotPresent
        val partialAmlsRecord =
          minimalUncleanCredsRecord.copy(amlsData = Some(AmlsData(amlsRegistered = true, None, None)))
        val tasks = await(taskListService.createTasks(partialAmlsRecord))

        tasks.length shouldBe 5
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.isComplete shouldBe false
      }
    }

    "contactDetailsTask" should {
      "when agent has no clean creds auth provider id and the amls task is complete show the contact details email task link" in {
        givenAmlsDataNotPresent
        val record = minimalUncleanCredsRecord.copy(
          amlsData =
            Some(AmlsData(amlsRegistered = false, Some(true), Some(AmlsDetails("supervisory", Left(PendingDetails(Some(LocalDate.now().minusDays(20))))))))
        )
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 5
        tasks(1).taskKey shouldBe "contactDetailsTask"
        tasks(1).subTasks.length shouldBe 3
        tasks(1).subTasks.head.showLink shouldBe true
        tasks(1).subTasks.tail.forall(_.showLink) shouldBe false
      }

      "when agent has no clean creds auth provider id and the email task is complete show the business name task link" in {
        givenAmlsDataNotPresent
        val record = minimalUncleanCredsRecord.copy(
          amlsData =
            Some(AmlsData(amlsRegistered = false, Some(true), Some(AmlsDetails("supervisory", Left(PendingDetails(Some(LocalDate.now().minusDays(20)))))))),
          contactEmailData = Some(ContactEmailData(true, Some("email@email.com")))
        )
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 5
        tasks(1).taskKey shouldBe "contactDetailsTask"
        tasks(1).subTasks.length shouldBe 3
        tasks(1).subTasks.head.showLink shouldBe true
        tasks(1).subTasks(1).showLink shouldBe true
        tasks(1).subTasks(2).showLink shouldBe false
      }

      "when agent has no clean creds auth provider id and the email and the business name tasks are complete show the business address task link" in {
        givenAmlsDataNotPresent
        val record = minimalUncleanCredsRecord.copy(
          amlsData =
            Some(AmlsData(amlsRegistered = false, Some(true), Some(AmlsDetails("supervisory", Left(PendingDetails(Some(LocalDate.now().minusDays(20)))))))),
          contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
          contactTradingNameData = Some(ContactTradingNameData(true, Some(tradingName)))
        )
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 5
        tasks(1).taskKey shouldBe "contactDetailsTask"
        tasks(1).subTasks.length shouldBe 3
        tasks(1).subTasks.head.showLink shouldBe true
        tasks(1).subTasks(1).showLink shouldBe true
        tasks(1).subTasks(2).showLink shouldBe true
      }
    }

    "MappingTask" should {
      "when agent has no clean creds auth provider id and the previous task is complete show the mapping task link" in {
        givenAmlsDataNotPresent
        val record = minimalUncleanCredsRecord.copy(
          amlsData =
            Some(AmlsData(amlsRegistered = false, Some(true), Some(AmlsDetails("supervisory", Left(PendingDetails(Some(LocalDate.now().minusDays(20)))))))),
          contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
          contactTradingAddressData = Some(ContactTradingAddressData(true, Some(tradingAddress))),
          contactTradingNameData = Some(ContactTradingNameData(true, Some(tradingName)))
        )
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 5
        tasks(2).taskKey shouldBe "mappingTask"
        tasks(2).subTasks.length shouldBe 1
        tasks(2).subTasks.head.showLink shouldBe true
      }

      "when an agent has completed mapping and the previous task is complete show the mapping task as complete" in {
        givenAmlsDataNotPresent
        val record = minimalUncleanCredsRecord.copy(
          mappingComplete = true,
          contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
          contactTradingNameData = Some(ContactTradingNameData(true, Some(tradingName))),
          contactTradingAddressData = Some(ContactTradingAddressData(true, Some(tradingAddress)))
        )
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 5
        tasks(2).taskKey shouldBe "mappingTask"
        tasks(2).isComplete shouldBe true
      }
    }

    "CreateIDTask" should {
      "when the previous task is complete show the create id link" in {
        givenAmlsDataNotPresent
        val record = minimalUncleanCredsRecord.copy(
          mappingComplete = true,
          amlsData =
            Some(AmlsData(amlsRegistered = true, None, Some(AmlsDetails("supervisory", Left(PendingDetails(Some(LocalDate.now().minusDays(20)))))))),
          contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
          contactTradingNameData = Some(ContactTradingNameData(true, Some(tradingName))),
          contactTradingAddressData = Some(ContactTradingAddressData(true, Some(tradingAddress)))
        )
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 5
        tasks(3).taskKey shouldBe "createIDTask"
        tasks(3).subTasks.length shouldBe 1
        tasks(3).subTasks.head.showLink shouldBe true
      }

      "when an agent has an auth provider id and the previous task is complete show the create id task as complete" in {
        givenAmlsDataNotPresent
        val record = minimalUncleanCredsRecord
          .copy(
            cleanCredsAuthProviderId = Some(AuthProviderId("cred-123")),
            mappingComplete = true,
            contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
            contactTradingNameData = Some(ContactTradingNameData(true, Some(tradingName))),
            contactTradingAddressData = Some(ContactTradingAddressData(true, Some(tradingAddress)))
          )
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 5
        tasks(3).taskKey shouldBe "createIDTask"
        tasks(3).subTasks.length shouldBe 1
        tasks(3).subTasks.head.isComplete shouldBe true
      }
    }

    "CheckAnswersTask" should {
      "when an agent has completed the previous task show the check answers link" in {
        givenAmlsDataNotPresent
        val record = minimalUncleanCredsRecord
          .copy(
            cleanCredsAuthProviderId = Some(AuthProviderId("cred-123")),
            mappingComplete = true,
            contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
            contactTradingNameData = Some(ContactTradingNameData(true, Some(tradingName))),
            contactTradingAddressData = Some(ContactTradingAddressData(true, Some(tradingAddress)))
          )
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 5
        tasks(4).taskKey shouldBe "checkAnswersTask"
        tasks(4).subTasks.length shouldBe 1
        tasks(4).subTasks.head.showLink shouldBe true
      }
    }
  }

  "when the user has clean creds show a reduced task list" should {
    "AmlsTask" should {
      "when agent doesn't has AMLS data show AMLS link" in {
        givenAmlsDataNotPresent
        val tasks = await(taskListService.createTasks(minimalCleanCredsRecord))

        tasks.length shouldBe 3
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.subTasks.length shouldBe 1
        tasks.head.subTasks.head.showLink shouldBe true
      }

      "when agent has registered AMLS data show AMLS as complete" in {
        givenAmlsDataNotPresent
        val amlsRecord = minimalCleanCredsRecord.copy(
          amlsData = Some(
            AmlsData(amlsRegistered = true, None, Some(AmlsDetails("supervisory", Right(RegisteredDetails("123", Some(LocalDate.now().plusDays(20)))))))))
        val tasks = await(taskListService.createTasks(amlsRecord))

        tasks.length shouldBe 3
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.isComplete shouldBe true
      }

      "when agent has pending AMLS data show AMLS as complete" in {
        givenAmlsDataNotPresent
        val amlsRecord = minimalCleanCredsRecord.copy(
          amlsData =
            Some(AmlsData(amlsRegistered = false, Some(true), Some(AmlsDetails("supervisory", Left(PendingDetails(Some(LocalDate.now().minusDays(20)))))))))
        val tasks = await(taskListService.createTasks(amlsRecord))

        tasks.length shouldBe 3
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.isComplete shouldBe true
      }

      "when agent has AMLS data show AMLS as complete" in {
        givenAmlsDataIsPresent
        when(stubAgentSubscriptionConnector.createOrUpdateJourney(any[SubscriptionJourneyRecord])(any[HeaderCarrier]))
          .thenReturn(Future.successful(1))
        val tasks = await(taskListService.createTasks(minimalCleanCredsRecord))
        tasks.length shouldBe 3
        tasks.head.isComplete shouldBe true
      }

      "when agent has partially completed AMLS details show AMLS as not complete" in {
        givenAmlsDataNotPresent
        val partialAmlsRecord =
          minimalCleanCredsRecord.copy(amlsData = Some(AmlsData(amlsRegistered = true, None, None)))
        val tasks = await(taskListService.createTasks(partialAmlsRecord))

        tasks.length shouldBe 3
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.isComplete shouldBe false
      }
    }

    "contactDetailsTask" should {
      "when an agent has completed amls task show the contact details task" in {
        givenAmlsDataNotPresent
        val record = minimalCleanCredsRecord.copy(
          amlsData =
            Some(AmlsData(amlsRegistered = false, Some(true), Some(AmlsDetails("supervisory", Left(PendingDetails(Some(LocalDate.now().minusDays(20)))))))))
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 3
        tasks(1).taskKey shouldBe "contactDetailsTask"
        tasks(1).subTasks.head.showLink shouldBe true

      }
    }

    "CheckAnswersTask" should {
      "when an agent has completed the previous task show the check answers link" in {
        givenAmlsDataNotPresent
        val record = minimalCleanCredsRecord.copy(
          amlsData =
            Some(AmlsData(amlsRegistered = false, Some(true), Some(AmlsDetails("supervisory", Left(PendingDetails(Some(LocalDate.now().minusDays(20)))))))),
          contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
          contactTradingNameData = Some(ContactTradingNameData(true, Some(tradingName))),
          contactTradingAddressData = Some(ContactTradingAddressData(true, Some(tradingAddress)))
        )

        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 3
        tasks(2).taskKey shouldBe "checkAnswersTask"
        tasks(2).subTasks.head.showLink shouldBe true
      }
    }
  }
}
