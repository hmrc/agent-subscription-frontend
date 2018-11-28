/*
 * Copyright 2017 HM Revenue & Customs
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

import org.jsoup.Jsoup
import uk.gov.hmrc.agentsubscriptionfrontend.config.amls.AMLSLoader
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{subscribingAgentEnrolledForNonMTD, subscribingCleanAgentWithoutEnrolments}

class AMLSControllerISpec extends BaseISpec {

  lazy val controller: AMLSController = app.injector.instanceOf[AMLSController]

  trait Setup {
    val authenticatedRequest = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
  }

  "showMoneyLaunderingComplianceForm (GET /money-laundering-compliance)" should {

    behave like anAgentAffinityGroupOnlyEndpoint(controller.showMoneyLaunderingComplianceForm(_))

    "contain page titles and header content" in new Setup {
      val result = await(controller.showMoneyLaunderingComplianceForm(authenticatedRequest))

      result should containMessages(
        "moneyLaunderingCompliance.title",
        "moneyLaunderingCompliance.p1"
      )
    }

    "ask for a money laundering supervisory body name from a list of acceptable values" in new Setup {
      val result = await(controller.showMoneyLaunderingComplianceForm(authenticatedRequest))
      val f = bodyOf(result)
      result should containMessages("moneyLaunderingCompliance.amls.title")

      val doc = Jsoup.parse(bodyOf(result))

      // Check form's radio inputs have correct values
      val elAmlsSelect = doc.getElementById("amls-auto-complete")
      elAmlsSelect should not be null
      elAmlsSelect.tagName() shouldBe "select"

      val amlsBodies = AMLSLoader.load("/amls.csv")
      amlsBodies.foreach{
        case (expectedCode, expectedName) => {
          val elFirstChoice = elAmlsSelect.getElementById(s"amlsCode-$expectedCode")
          elFirstChoice should not be null
          elFirstChoice.attr("value") shouldBe expectedCode
          elFirstChoice.text() shouldBe expectedName
        }
      }
    }

    "ask for membership number" in new Setup {
      val result = await(controller.showMoneyLaunderingComplianceForm(authenticatedRequest))

      result should containMessages("moneyLaunderingCompliance.membershipNumber.title")
      result should containInputElement("membershipNumber", "text")
    }

    "ask for membership expiry date" in new Setup {
      val result = await(controller.showMoneyLaunderingComplianceForm(authenticatedRequest))

      result should containMessages(
        "moneyLaunderingCompliance.expiry.title",
        "moneyLaunderingCompliance.expiry.hint",
        "moneyLaunderingCompliance.expiry.day.title",
        "moneyLaunderingCompliance.expiry.month.title",
        "moneyLaunderingCompliance.expiry.year.title"
      )
      result should containInputElement("expiry.day", "text")
      result should containInputElement("expiry.month", "text")
      result should containInputElement("expiry.year", "text")
    }

    "contain a continue button" in new Setup {
      val result = await(controller.showMoneyLaunderingComplianceForm(authenticatedRequest))

      result should containSubmitButton(
        expectedMessageKey = "moneyLaunderingCompliance.continue",
        expectedElementId = "continue"
      )
    }
  }

//  "submitMoneyLaunderingComplianceForm (POST /business-type)" should {
//    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitMoneyLaunderingComplianceForm(_))
//
//    pending
//  }
}
