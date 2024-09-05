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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers.business

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.jsoup.select.Elements
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.{BusinessIdentificationController, routes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, AuthProviderId, BusinessType}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingCleanAgentWithoutEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, TestSetupNoJourneyRecord}

class BusinessNameISpecIt extends BaseISpecIt {

  lazy val controller: BusinessIdentificationController = app.injector.instanceOf[BusinessIdentificationController]

  "showBusinessNameForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showBusinessNameForm(request))

    "display business name form if the name is des complaint" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

      val result: Result = await(controller.showBusinessNameForm(request))
      result should containMessages("businessName.title", "businessName.description", "button.continue")
      val doc: Document = Jsoup.parse(bodyOf(result))

      doc.getElementById("name").`val` shouldBe "My Agency"

      val backLink: Elements = doc.getElementsByClass("govuk-back-link")
      backLink.attr("href") shouldBe routes.SubscriptionController.showCheckAnswers().url

      val form: Element = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitBusinessNameForm().url
    }

    "display business name form if the name is not des complaint" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession = Some(
        AgentSession(
          Some(BusinessType.SoleTrader),
          utr = Some(validUtr),
          registration = Some(testRegistration.copy(taxpayerName = Some("My Agency &")))
        )
      )

      val result: Result = await(controller.showBusinessNameForm(request))
      result should containMessages("businessName.updated.title", "businessName.updated.p1", "businessName.description", "button.continue")
      val doc: Document = Jsoup.parse(bodyOf(result))
      doc.getElementById("name").`val` shouldBe "My Agency &"

      val form: Element = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitBusinessNameForm().url
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result: Result = await(controller.showBusinessNameForm(request))

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }

  "submitBusinessNameForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitBusinessNameForm(request))

    "update business name after submission, redirect to task list when there is a continue url" in new TestSetupNoJourneyRecord {
      val agentSession: AgentSession =
        AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration), postcode = Some("AA1 1AA"))
      val sjr: SubscriptionJourneyRecord = SubscriptionJourneyRecord.fromAgentSession(agentSession, AuthProviderId("12345-credId"))
      val newSjr: SubscriptionJourneyRecord = sjr.copy(
        continueId = None,
        businessDetails = sjr.businessDetails.copy(
          registration = Some(testRegistration.copy(taxpayerName = Some("new Agent name")))
        )
      )
      givenSubscriptionRecordCreated(AuthProviderId("12345-credId"), newSjr)
      givenAgentIsNotManuallyAssured(validUtr)
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST).withFormUrlEncodedBody("name" -> "new Agent name")
      sessionStoreService.currentSession.agentSession = Some(agentSession)
      sessionStoreService.currentSession.continueUrl = Some("/continue/url")

      val result: Result = await(controller.submitBusinessNameForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.TaskListController.showTaskList().url

      sessionStoreService.currentSession.agentSession.get.registration.get.taxpayerName shouldBe Some("new Agent name")
    }

    "update business name after submission and redirect to /check-answers if user is changing answers" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST).withFormUrlEncodedBody("name" -> "new Agent name")
      sessionStoreService.currentSession.changingAnswers = Some(true)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

      val result: Result = await(controller.submitBusinessNameForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.SubscriptionController.showCheckAnswers().url
    }

    "show validation error when the form is submitted with empty name" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST).withFormUrlEncodedBody("name" -> "")
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))
      val result: Result = await(controller.submitBusinessNameForm(request))

      result should containMessages("businessName.title", "error.business-name.empty")
    }

    "show validation error when the form is submitted with non des complaint name after check-answers page" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST).withFormUrlEncodedBody("name" -> "Some name *")
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))
      val result: Result = await(controller.submitBusinessNameForm(request))

      result should containMessages("businessName.title", "error.business-name.invalid")
    }

    "show validation error when the form is submitted with non des complaint name" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST).withFormUrlEncodedBody("name" -> "Some name *")
      sessionStoreService.currentSession.agentSession = Some(
        AgentSession(
          Some(BusinessType.SoleTrader),
          utr = Some(validUtr),
          registration = Some(testRegistration.copy(taxpayerName = Some("Some name *")))
        )
      )

      val result: Result = await(controller.submitBusinessNameForm(request))

      result should containMessages("businessName.updated.title", "error.business-name.invalid")
    }
  }

}
