package uk.gov.hmrc.agentsubscriptionfrontend.controllers
import java.time.LocalDate

import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.DateOfBirth
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.domain.Nino

class DateOfBirthControllerSpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: DateOfBirthController = app.injector.instanceOf[DateOfBirthController]

  "GET /date-of-birth page" should {

    "display the page with expected content" in {

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showDateOfBirthForm()(request))

      result should containMessages("date-of-birth.title", "date-of-birth.hint")
    }
  }

  "POST /date-of-birth form" should {
    "read the dob as expected and save it to the session" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "1950")
      givenAGoodCombinationNinoAndDobMatchCitizenDetails(Nino("AE123456C"), DateOfBirth(LocalDate.parse("1950-01-01")))
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.VatDetailsController.showRegisteredForVatForm().url)

      val dob = DateOfBirth(LocalDate.of(1950, 1, 1))

      sessionStoreService.currentSession.agentSession shouldBe Some(agentSession.copy(dateOfBirth = Some(dob)))
    }

    "show the error page when the nino and date of birth details have not matched in citizen details" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "1950")
      givenABadCombinationNinoAndDobDoNotMatch(Nino("AE123456C"), DateOfBirth(LocalDate.parse("1950-01-01")))
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("noAgencyFound.title", "noAgencyFound.p1", "noAgencyFound.p2")
    }

    "handle forms with date-of-birth in future" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "2030")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.must.be.past")
    }

    "handle forms with date-of-birth earlier than 1900" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "1899")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.is.not.real")
    }

    "handle forms with date-of-birth fields as non-digits" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.day" -> "xx", "dob.month" -> "11", "dob.year" -> "2010")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.invalid")
    }

    "display consolidated error when month and year are empty" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.day" -> "1")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.month-year.empty")
    }

    "display consolidated error when day and year are empty" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.month" -> "1")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.day-year.empty")
    }

    "display consolidated error when day and month are empty" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.year" -> "1980")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.day-month.empty")
    }
  }

}
