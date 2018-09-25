/*
 * Copyright 2018 HM Revenue & Customs
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

import org.mockito.Mockito.{times, verify, when}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.MappingConnector
import uk.gov.hmrc.agentsubscriptionfrontend.service.SubscriptionService
import uk.gov.hmrc.agentsubscriptionfrontend.support.ResettingMockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import scala.concurrent.{ExecutionContext, Future}

class CommonRoutingSpec extends UnitSpec with WithFakeApplication with ResettingMockitoSugar {
  private val mockMappingConnector = resettingMock[MappingConnector]
  private val mockSubscriptionService = resettingMock[SubscriptionService]
  private val mockAppConfig = resettingMock[AppConfig]

  private val commonRouting =
    new CommonRouting(mockMappingConnector, mockSubscriptionService, mockAppConfig)

  private val utr = Utr("9876543210")
  private implicit val hc = HeaderCarrier()
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

//  "completeMappingWhenAvailable" should {
//    implicit def request = FakeRequest().withSession("performAutoMapping" -> "true")
//
//    "redirect to showSubscriptionComplete" when {
//      "autoMapping is on and they are eligible for mapping" in {
//        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(true)
//        when(mockMappingConnector.updatePreSubscriptionWithArn(utr)).thenReturn(Future successful ())
//
//        val result = commonRouting.completeMappingWhenAvailable(utr)
//        status(result) shouldBe 303
//        verify(mockMappingConnector, times(1)).updatePreSubscriptionWithArn(utr)
//
//        redirectLocation(result).get shouldBe routes.SubscriptionController.showSubscriptionComplete().url
//      }
//
//      "autoMapping is ON and they are not eligible for mapping hence why was not offered the decision to add decision to session" in {
//        implicit def request = FakeRequest()
//
//        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(false)
//
//        val result = commonRouting.completeMappingWhenAvailable(utr)
//        status(result) shouldBe 303
//        verify(mockMappingConnector, times(0)).updatePreSubscriptionWithArn(utr)
//
//        redirectLocation(result).get shouldBe routes.SubscriptionController.showSubscriptionComplete().url
//      }
//
//      "autoMapping is OFF do not attempt mapping even if decision found in session" in {
//        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(false)
//
//        val result = commonRouting.completeMappingWhenAvailable(utr)
//        status(result) shouldBe 303
//        verify(mockMappingConnector, times(0)).updatePreSubscriptionWithArn(utr)
//
//        redirectLocation(result).get shouldBe routes.SubscriptionController.showSubscriptionComplete().url
//      }
//    }
//  }

  "handleAutoMapping" should {
    "handleAutoMapping decide whether a user can see showLinkClients page needed for mapping" when {
      implicit def request = FakeRequest()

      "autoMapping is ON and they ARE ELIGIBLE for mapping SHOW linkClients page " in {
        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(true)

        val result = await(commonRouting.handleAutoMapping(eligibleForMapping = Some(true)))
        redirectLocation(result).get shouldBe routes.SubscriptionController.showLinkClients().url
      }

      "autoMapping is ON and they are NOT ELIGIBLE for mapping hence why was NOT OFFERED the decision to add decision to session" in {
        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(true)

        val result = await(commonRouting.handleAutoMapping(eligibleForMapping = Some(false)))
        redirectLocation(result).get shouldBe routes.SubscriptionController.showCheckAnswers().url
      }

      "autoMapping is ON and chainedSessionDetails did NOT CACHE wasEligibleForMapping" in {
        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(true)

        val result = commonRouting.handleAutoMapping(eligibleForMapping = None)
        status(result) shouldBe 303

        redirectLocation(result).get shouldBe routes.SubscriptionController.showCheckAnswers().url
      }

      "autoMapping is OFF" in {
        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(false)

        val result = commonRouting.handleAutoMapping(None)
        status(result) shouldBe 303

        redirectLocation(result).get shouldBe routes.SubscriptionController.showCheckAnswers().url
      }
    }
  }

//  "handlePartialSubscription" should {
//    implicit val fakeRequest = FakeRequest()
//    implicit val request = (mappingEligibility: Option[Boolean]) =>
//      commonRouting.handlePartialSubscription(utr, "AA11AA", mappingEligibility)
//
//    "showSubscriptionComplete" when {
//
//      "user not eligible for mapping " in {
//        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(false)
//        when(mockSubscriptionService.completePartialSubscription(utr, postcode)).thenReturn(Future successful arn)
//        mockMetrics("Count-Subscription-PartialSubscriptionCompleted")
//
//        val result = await(commonRouting.handlePartialSubscription(utr, "AA11AA", Some(false)))
//
//        status(result) shouldBe 303
//        redirectLocation(result).get shouldBe routes.SubscriptionController.showSubscriptionComplete().url
//        verifyMetricCalled("Count-Subscription-PartialSubscriptionCompleted")
//      }
//
//      "autoMapping is off still complete partial subscription" in {
//        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(false)
//        when(mockSubscriptionService.completePartialSubscription(utr, postcode)).thenReturn(Future successful arn)
//        mockMetrics("Count-Subscription-PartialSubscriptionCompleted")
//
//        val result = commonRouting.handlePartialSubscription(utr, "AA11AA", None)
//
//        status(result) shouldBe 303
//        redirectLocation(result).get shouldBe routes.SubscriptionController.showSubscriptionComplete().url
//        verifyMetricCalled("Count-Subscription-PartialSubscriptionCompleted")
//      }
//    }
//
//    "showLinkClients" when {
//      "showLinkClientsis eligible and mapping is on" in {
//        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(true)
//        when(mockSubscriptionService.completePartialSubscription(utr, postcode)).thenReturn(Future successful arn)
//
//        val result = commonRouting.handlePartialSubscription(utr, "AA11AA", Some(true))
//
//        status(result) shouldBe 303
//        redirectLocation(result).get shouldBe routes.SubscriptionController.showLinkClients().url
//      }
//    }
//  }
}
