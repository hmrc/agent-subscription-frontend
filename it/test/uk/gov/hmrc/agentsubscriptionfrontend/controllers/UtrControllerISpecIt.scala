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
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessType}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{validBusinessTypes, validUtr}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestSetupNoJourneyRecord}

import scala.concurrent.ExecutionContext.Implicits.global

class UtrControllerISpecIt extends BaseISpecIt with SessionDataMissingSpec {

  lazy val controller: UtrController = app.injector.instanceOf[UtrController]

  private lazy val messagesApi = app.injector.instanceOf[MessagesApi]
  private implicit lazy val messages: Messages = messagesApi.preferred(Seq.empty[Lang])

  "showUtrFormPage" should {

    validBusinessTypes.foreach { businessType =>
      s"display the page as expected when is business type is $businessType" in new TestSetupNoJourneyRecord {

        implicit val request: FakeRequest[AnyContentAsEmpty.type] =
          authenticatedAs(subscribingAgentEnrolledForNonMTD)
        await(sessionStoreService.cacheAgentSession(AgentSession(Some(businessType)))(request, global, aesCrypto))

        private val result = await(controller.showUtrForm()(request))

        result should containMessages(
          s"utr.header.${businessType.key}"
        )

        result should containSubstrings(Messages(s"utr.description.about.${businessType.key}"))
      }
    }

    "pre-populate the utr if one is already stored in the session" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsEmpty.type] =
        authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader), Some("abcd")))(request, global, aesCrypto))

      private val result = await(controller.showUtrForm()(request))

      result should containInputElement("utr", "text", Some("abcd"))
    }
  }

  "submitUtrFormPage" should {

    "display the page as expected when the form is valid and redirect to /postcode page" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("utr" -> validUtr)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(BusinessType.SoleTrader)))

      private val result = await(controller.submitUtrForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.PostcodeController.showPostcodeForm().url)
    }

    "redirect to /business-type if businessType missing in the session" in new TestSetupNoJourneyRecord {

      private val request = authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("utr" -> validUtr)
      private val result = await(controller.submitUtrForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }

    "handle form with incorrect formatted utr error and show the same again" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("utr" -> "abcdefghij")
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(BusinessType.SoleTrader)))

      private val result = await(controller.submitUtrForm()(request))

      status(result) shouldBe 200
      result should containMessages(
        s"utr.header.${BusinessType.SoleTrader.key}",
        "error.sautr.incorrectFormat"
      )
    }

    "handle form with invalid utr error and show the same again" in new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingAgentEnrolledForNonMTD, POST).withFormUrlEncodedBody("utr" -> "1234567890")
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(BusinessType.SoleTrader)))

      private val result = await(controller.submitUtrForm()(request))

      status(result) shouldBe 200
      result should containMessages(
        s"utr.header.${BusinessType.SoleTrader.key}",
        "error.sautr.invalid"
      )
    }
  }
}
