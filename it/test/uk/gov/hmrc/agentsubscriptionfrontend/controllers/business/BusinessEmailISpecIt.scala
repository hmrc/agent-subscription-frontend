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

import scala.concurrent.ExecutionContext.Implicits.global

class BusinessEmailISpecIt extends BaseISpecIt {

  lazy val controller: BusinessIdentificationController = app.injector.instanceOf[BusinessIdentificationController]

  "showBusinessEmailForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showBusinessEmailForm(request))

    "display business email form" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

      val result: Result = await(controller.showBusinessEmailForm(request))
      result should containMessages("businessEmail.title", "businessEmail.description", "button.continue")
      val doc: Document = Jsoup.parse(bodyOf(result))
      doc.getElementById("email").`val` shouldBe "test@gmail.com"

      val backLink: Elements = doc.getElementsByClass("govuk-back-link")
      backLink.attr("href") shouldBe routes.SubscriptionController.showCheckAnswers().url

      val form: Element = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitBusinessEmailForm().url
    }

    "display business email form when email address in initial details is empty" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration.copy(emailAddress = None))))

      val result: Result = await(controller.showBusinessEmailForm(request))
      result should containMessages("businessEmail.title", "businessEmail.description", "button.continue")
      val doc: Document = Jsoup.parse(bodyOf(result))
      doc.getElementById("email").`val` shouldBe ""

      val form: Element = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitBusinessEmailForm().url
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result: Result = await(controller.showBusinessNameForm(request))

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }

  "submitBusinessEmailForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitBusinessEmailForm(request))

    "update business email after submission, redirect to task list when there is a continue url" in new TestSetupNoJourneyRecord {
      val agentSession: AgentSession =
        AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration), postcode = Some("AA11AA"))
      givenAgentIsNotManuallyAssured(validUtr)

      val sjr: SubscriptionJourneyRecord = SubscriptionJourneyRecord.fromAgentSession(agentSession, AuthProviderId("12345-credId"))
      val newsjr: SubscriptionJourneyRecord = sjr.copy(
        continueId = None,
        businessDetails = sjr.businessDetails.copy(registration = Some(testRegistration.copy(emailAddress = Some("newagent@example.com"))))
      )

      givenSubscriptionRecordCreated(AuthProviderId("12345-credId"), newsjr)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST).withFormUrlEncodedBody("email" -> "newagent@example.com")
      sessionStoreService.currentSession.agentSession = Some(agentSession)
      sessionStoreService.currentSession.continueUrl = Some("/continue/url")

      val result: Result = await(controller.submitBusinessEmailForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.TaskListController.showTaskList().url

      await(sessionStoreService.fetchAgentSession(request, global, aesCrypto)).get.registration.get.emailAddress shouldBe Some("newagent@example.com")
    }

    "update business email after submission and redirect to /check-answers if user is changing answers" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST).withFormUrlEncodedBody("email" -> "newagent@example.com")
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))
      sessionStoreService.currentSession.changingAnswers = Some(true)

      val result: Result = await(controller.submitBusinessEmailForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.SubscriptionController.showCheckAnswers().url

      await(sessionStoreService.fetchIsChangingAnswers) shouldBe Some(true)
    }

    "show validation error when the form is submitted with empty email" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments, POST).withFormUrlEncodedBody("email" -> "")
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

      val result: Result = await(controller.submitBusinessEmailForm(request))

      result should containMessages("businessEmail.title", "error.business-email.empty")
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result: Result = await(controller.showBusinessNameForm(request))

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }

}
