package uk.gov.hmrc.agentsubscriptionfrontend.support

import uk.gov.hmrc.agentsubscriptionfrontend.models.IdentifyBusinessType
import uk.gov.hmrc.play.test.UnitSpec

class BindersSpec extends UnitSpec {

  "Binders.businessTypeBinder.bind" should {
    "successful NORMAL binding" when {
      "bind correctly" in {

        val soleTrader = Binders.businessTypeBinder.bind("businessType", Map("businessType" -> Seq("sole_trader")))
        soleTrader.get shouldBe Right(IdentifyBusinessType.SoleTrader)

        val limitedCo = Binders.businessTypeBinder.bind("businessType", Map("businessType" -> Seq("limited_company")))
        limitedCo.get shouldBe Right(IdentifyBusinessType.LimitedCompany)

        val partnership = Binders.businessTypeBinder.bind("businessType", Map("businessType" -> Seq("partnership")))
        partnership.get shouldBe Right(IdentifyBusinessType.Partnership)

        val llpBind = Binders.businessTypeBinder.bind("businessType", Map("businessType" -> Seq("llp")))
        llpBind.get shouldBe Right(IdentifyBusinessType.Llp)
      }

      "successful BAD binding" when {
        "Left when binding results in IdentifyBusinessType.Undefined => BadRequest as empty value" in {
          val undefinedEmpty = Binders.businessTypeBinder.bind("businessType", Map("businessType" -> Seq("")))
          undefinedEmpty.get shouldBe Left("Submitted form value did not contain valid businessType identifier")

          val undefinedBadInput = Binders.businessTypeBinder.bind("businessType", Map("businessType" -> Seq("someInvalidType")))
          undefinedBadInput.get shouldBe Left("Submitted form value did not contain valid businessType identifier")
        }
      }
    }
  }

  "Binders.businessTypeBinder.bind" should {
    "unbind" in {
      Binders.businessTypeBinder.unbind("businessType", IdentifyBusinessType.SoleTrader) shouldBe "businessType=sole_trader"
      Binders.businessTypeBinder.unbind("businessType", IdentifyBusinessType.LimitedCompany) shouldBe "businessType=limited_company"
      Binders.businessTypeBinder.unbind("businessType", IdentifyBusinessType.Partnership) shouldBe "businessType=partnership"
      Binders.businessTypeBinder.unbind("businessType", IdentifyBusinessType.Llp) shouldBe "businessType=llp"

      //Undefined "businessType=invalid" shouldBe filtered and rejected
      Binders.businessTypeBinder.unbind("businessType", IdentifyBusinessType.Undefined) shouldBe "businessType=invalid"
    }
  }
}
