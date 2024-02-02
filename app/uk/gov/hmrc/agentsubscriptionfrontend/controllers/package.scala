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

package uk.gov.hmrc.agentsubscriptionfrontend

import play.api.Logging
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.mvc.{AnyContent, Call, Request}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TaxIdentifierFormatters.normalizedText
import uk.gov.hmrc.agentsubscriptionfrontend.validators.CommonValidators._
import uk.gov.hmrc.domain.Nino
import uk.gov.voa.play.form.ConditionalMappings.{mandatoryIfEqual, mandatoryIfTrue}

package object controllers extends Logging {

  def continueOrStop(next: Call, previous: Call)(implicit request: Request[AnyContent]): Call = {

    val submitAction = request.body.asFormUrlEncoded
      .fold(Seq.empty: Seq[String])(someMap => someMap.getOrElse("submit", Seq.empty))

    val call = submitAction.headOption match {
      case Some("continue") => next
      case Some("save")     => routes.TaskListController.savedProgress(Some(previous.url))
      case e                => throw new Exception(s"unexpected value in submit $e")
    }
    call
  }

  object BusinessIdentificationForms {

    private val businessTypes = List(BusinessType.LimitedCompany.key, BusinessType.Llp.key, BusinessType.Partnership.key, BusinessType.SoleTrader.key)

    val businessTypeForm: Form[BusinessType] =
      Form[BusinessType](
        mapping(
          "businessType" -> optional(text)
            .transform[String](_.getOrElse(""), s => Some(s))
            .verifying("businessType.error.invalid-choice", value => businessTypes.contains(value))
        )(input => BusinessType(input))(bType => Some(bType.key))
      )

    def utrForm(businessType: String): Form[Utr] =
      Form[Utr](
        mapping("utr" -> businessUtr(businessType))(input => Utr(input.replaceAll(" ", "")))(utr => Some(utr.value))
      )

    def postcodeForm: Form[Postcode] =
      Form[Postcode](
        mapping("postcode" -> postcode)(input => Postcode(input))(postcode => Some(postcode.value))
      )

    def ninoForm: Form[Nino] =
      Form[Nino](mapping("nino" -> text.transform[String](normaliseNino, _.toString).verifying(ninoConstraint))(Nino.apply)(Nino.unapply))

    private def normaliseNino(ninoStr: String): String = ninoStr.replaceAll("\\s", "").toUpperCase

    val confirmBusinessForm: Form[ConfirmBusiness] =
      Form[ConfirmBusiness](
        mapping("confirmBusiness" -> optional(text).verifying(radioInputSelected("error.confirm-business-value.invalid")))(answer =>
          ConfirmBusiness(RadioInputAnswer.apply(answer.getOrElse(""))))(answer => Some(RadioInputAnswer.unapply(answer.confirm)))
          .verifying(
            "error.confirm-business-value.invalid",
            submittedAnswer => Seq(Yes, No).contains(submittedAnswer.confirm)
          ))

    val businessEmailForm = Form[BusinessEmail](
      mapping(
        "email" -> emailAddress
      )(BusinessEmail.apply)(BusinessEmail.unapply)
    )

    val businessNameForm = Form[BusinessName](
      mapping(
        "name" -> businessName
      )(BusinessName.apply)(BusinessName.unapply)
    )

    val updateBusinessAddressForm = Form[UpdateBusinessAddressForm](
      mapping(
        "addressLine1" -> addressLine1,
        "addressLine2" -> addressLine234(lineNumber = 2),
        "addressLine3" -> addressLine234(lineNumber = 3),
        "addressLine4" -> addressLine234(lineNumber = 4),
        "postcode"     -> postcode
      )(UpdateBusinessAddressForm.apply)(UpdateBusinessAddressForm.unapply))

    //uses variant "cannotProvide" to determine action if user cannot provide allowed options: utr or nino
    val clientDetailsForm: Form[RadioInvasiveTaxPayerOption] = Form[RadioInvasiveTaxPayerOption](
      mapping(
        "variant" -> optional(text)
          .transform[String](_.getOrElse(""), s => Some(s))
          .verifying(radioInputStringSelected("clientDetails.error.no-radio.selected")),
        "utr" -> mandatoryIfEqual("variant", "utr", clientDetailsUtr)
          .transform[String](u => u.getOrElse("").replace(" ", "").toUpperCase(), s => Some(s)),
        "nino" -> mandatoryIfEqual("variant", "nino", clientDetailsNino)
          .transform[String](n => n.getOrElse("").replace(" ", "").toUpperCase(), s => Some(s))
      )(RadioInvasiveTaxPayerOption.apply)(RadioInvasiveTaxPayerOption.unapply))

    val invasiveCheckStartSaAgentCodeForm: Form[RadioInvasiveStartSaAgentCode] = Form[RadioInvasiveStartSaAgentCode](
      mapping(
        "hasSaAgentCode" -> optional(normalizedText)
          .transform[String](_.getOrElse(""), s => Some(s))
          .verifying(radioInputStringSelected("invasive.error.no-radio.selected")),
        "saAgentCode" -> mandatoryIfTrue("hasSaAgentCode", saAgentCode)
          .transform[String](_.getOrElse(""), s => Some(s))
      )((hasSaAgentCode, saAgentCode) => RadioInvasiveStartSaAgentCode(hasSaAgentCode.toBoolean, saAgentCode))(radioInvasiveStartSaAgentCode =>
        Some((radioInvasiveStartSaAgentCode.hasSaAgentCode.toString, radioInvasiveStartSaAgentCode.saAgentCode))))
  }

  object CompanyRegistrationForms {
    val crnForm: Form[CompanyRegistrationNumber] =
      Form[CompanyRegistrationNumber](
        mapping("crn" -> crn)(input => CompanyRegistrationNumber(input))(crn => Some(crn.value))
      )
  }

  object AMLSForms {

    def checkAmlsForm: Form[RadioInputAnswer] =
      Form[RadioInputAnswer](
        mapping("registeredAmls" -> optional(text)
          .verifying("error.check-amls-value.invalid", a => a.contains("yes") || a.contains("no")))(a => RadioInputAnswer.apply(a.getOrElse("")))(a =>
          Some(RadioInputAnswer.unapply(a))))

    def appliedForAmlsForm: Form[RadioInputAnswer] =
      Form[RadioInputAnswer](
        mapping("amlsAppliedFor" -> optional(text)
          .verifying("error.check-amlsAppliedFor-value.invalid", a => a.contains("yes") || a.contains("no")))(a =>
          RadioInputAnswer.apply(a.getOrElse("")))(a => Some(RadioInputAnswer.unapply(a))))

    val HMRC_AMLS_ERROR = "error.moneyLaunderingCompliance.membershipNumber.invalid"

    def amlsForm(bodies: Set[String]): Form[AMLSForm] =
      Form[AMLSForm](
        mapping(
          "amlsCode"         -> amlsCode(bodies),
          "membershipNumber" -> membershipNumber,
          "expiry"           -> expiryDate
        )(AMLSForm.apply)(AMLSForm.unapply).verifying(HMRC_AMLS_ERROR, { (o: AMLSForm) =>
          o match {
            case AMLSForm(code, number, _) if code == "HMRC" => number.matches(amlsRegex)
            case AMLSForm(_, _, _)                           => true
          }
        }))

    val HMRC_AMLS_Empty_ERROR = "error.moneyLaunderingCompliance.membershipNumber.empty"
    val membershipNumberConstraint: Constraint[String] = Constraint("constraints.membershipNumber") { input =>
      val errors = input match {
        case ""                                    => Seq(ValidationError(HMRC_AMLS_Empty_ERROR))
        case x if !x.matches(amlsRegex) && x != "" => Seq(ValidationError(HMRC_AMLS_ERROR))
        case _                                     => Nil
      }
      if (errors.isEmpty) Valid else Invalid(errors)
    }
    def amlsEnterNumberForm(): Form[EnterAMLSNumberForm] =
      Form[EnterAMLSNumberForm](
        mapping(
          "membershipNumber" -> membershipNumber
            .verifying(membershipNumberConstraint)
        )(EnterAMLSNumberForm.apply)(EnterAMLSNumberForm.unapply)
      )

    private def amlsRegex = "X[A-Z]ML00000[0-9]{6}"

    def enterAmlsExpiryDateForm: Form[EnterAMLSExpiryDateForm] =
      Form[EnterAMLSExpiryDateForm](
        mapping(
          "expiry" -> expiryDate
        )(EnterAMLSExpiryDateForm.apply)(EnterAMLSExpiryDateForm.unapply))

    import play.api.data.{Form, FormError}

    def formWithRefinedErrors(form: Form[AMLSForm]): Form[AMLSForm] = {

      val expiry = "expiry"
      val dateFields =
        (error: FormError) => error.key == s"$expiry.day" || error.key == s"$expiry.month" || error.key == s"$expiry.year"

      def refineErrors(dateFieldErrors: Seq[FormError]): Option[String] =
        dateFieldErrors.map(_.key).map(k => "expiry.".r.replaceFirstIn(k, "")).sorted match {
          case List("day", "month", "year") => Some("error.moneyLaunderingCompliance.date.empty")
          case List("day", "month")         => Some("error.moneyLaunderingCompliance.day.month.empty")
          case List("day", "year")          => Some("error.moneyLaunderingCompliance.day.year.empty")
          case List("day")                  => Some("error.moneyLaunderingCompliance.day.empty")
          case List("month", "year")        => Some("error.moneyLaunderingCompliance.month.year.empty")
          case List("month")                => Some("error.moneyLaunderingCompliance.month.empty")
          case List("year")                 => Some("error.moneyLaunderingCompliance.year.empty")
          case _                            => None
        }

      val dateFieldErrors: Seq[FormError] = form.errors.filter(dateFields)

      val refinedMessage = refineErrors(dateFieldErrors).getOrElse("")

      dateFieldErrors match {
        case Nil =>
          form.copy(errors = form.errors collect {
            case FormError(_, message, _) if message == Seq(HMRC_AMLS_ERROR) => FormError("membershipNumber", List(HMRC_AMLS_ERROR))
            case a: FormError                                                => a
          })
        case _ =>
          form.copy(errors = form.errors.map { error =>
            if (error.key.contains("expiry")) {
              FormError(error.key, "", error.args)
            } else error
          }.toList :+ FormError(key = "expiry", message = refinedMessage, args = Seq()))
      }
    }

    def amlsPendingDetailsFormWithRefinedErrors(form: Form[EnterAMLSExpiryDateForm]): Form[EnterAMLSExpiryDateForm] = {

      val expiry = "expiry"
      val dateFields =
        (error: FormError) => error.key == s"$expiry.day" || error.key == s"$expiry.month" || error.key == s"$expiry.year"

      def refineErrors(dateFieldErrors: Seq[FormError]): Option[String] =
        dateFieldErrors.map(_.key).map(k => "expiry.".r.replaceFirstIn(k, "")).sorted match {
          case List("day", "month", "year") => Some("error.amls.enter-renewal-date.all.empty")
          case List("day", "month")         => Some("error.amls.enter-renewal-date.day.month.empty")
          case List("day", "year")          => Some("error.amls.enter-renewal-date.day.year.empty")
          case List("day")                  => Some("error.amls.enter-renewal-date.day.empty")
          case List("month", "year")        => Some("error.amls.enter-renewal-date.month.year.empty")
          case List("month")                => Some("error.amls.enter-renewal-date.month.empty")
          case List("year")                 => Some("error.amls.enter-renewal-date.year.empty")
          case _                            => None
        }

      val dateFieldErrors: Seq[FormError] = form.errors.filter(dateFields)

      val refinedMessage = refineErrors(dateFieldErrors).getOrElse("")

      dateFieldErrors match {
        case Nil => form
        case _ =>
          form.copy(errors = form.errors.map { error =>
            if (error.key.contains(expiry)) {
              FormError(error.key, "", error.args)
            } else error
          }.toList :+ FormError(key = expiry, message = refinedMessage, args = Seq()))
      }
    }
  }

  object ContactDetailsForms {

    val contactEmailCheckForm = Form[ContactEmailCheck](
      mapping("check" -> optional(text).verifying(radioInputSelected("error.contact-email-check.invalid")))(answer =>
        ContactEmailCheck(RadioInputAnswer.apply(answer.getOrElse(""))))(answer => Some(RadioInputAnswer.unapply(answer.check)))
        .verifying(
          "error.contact-email-check.invalid",
          submittedAnswer => Seq(Yes, No).contains(submittedAnswer.check)
        )
    )

    val contactEmailAddressForm = Form[BusinessEmail](
      mapping(
        "email" -> contactEmailAddress
      )(BusinessEmail.apply)(BusinessEmail.unapply)
    )

    val contactTradingNameCheckForm = Form[ContactTradingNameCheck](
      mapping("check" -> optional(text).verifying(radioInputSelected("error.contact-trading-name-check.invalid")))(answer =>
        ContactTradingNameCheck(RadioInputAnswer.apply(answer.getOrElse(""))))(answer => Some(RadioInputAnswer.unapply(answer.check)))
        .verifying(
          "error.contact-trading-name-check.invalid",
          submittedAnswer => Seq(Yes, No).contains(submittedAnswer.check)
        )
    )

    val contactTradingNameForm = Form[BusinessName](
      mapping(
        "name" -> tradingName
      )(BusinessName.apply)(BusinessName.unapply)
    )

    val contactTradingAddressCheckForm = Form[ContactTradingAddressCheck](
      mapping("check" -> optional(text).verifying(radioInputSelected("error.contact-trading-address-check.invalid")))(answer =>
        ContactTradingAddressCheck(RadioInputAnswer.apply(answer.getOrElse(""))))(answer => Some(RadioInputAnswer.unapply(answer.check)))
        .verifying(
          "error.contact-trading-address-check.invalid",
          submittedAnswer => Seq(Yes, No).contains(submittedAnswer.check)
        )
    )

    val contactPhoneCheckForm = Form[ContactPhoneCheck](
      mapping("check" -> optional(text).verifying(radioInputSelected("error.contact-phone-check.invalid")))(answer =>
        ContactPhoneCheck(RadioInputAnswer.apply(answer.getOrElse(""))))(answer => Some(RadioInputAnswer.unapply(answer.check)))
        .verifying(
          "error.contact-phone-check.invalid",
          submittedAnswer => Seq(Yes, No).contains(submittedAnswer.check)
        )
    )

    private val ukPhoneNumberRegex =
      "^(?:(?:\\(?(?:0(?:0|11)\\)?[\\s-]?\\(?|\\+)44\\)?[\\s-]?(?:\\(?0\\)?[\\s-]?)?)|(?:\\(?0))(?:(?:\\d{5}\\)?[\\s-]?" +
        "\\d{4,5})|(?:\\d{4}\\)?[\\s-]?(?:\\d{5}|\\d{3}[\\s-]?\\d{3}))|(?:\\d{3}\\)?[\\s-]?\\d{3}[\\s-]?\\d{3,4})|" +
        "(?:\\d{2}\\)?[\\s-]?\\d{4}[\\s-]?\\d{4}))(?:[\\s-]?(?:x|ext\\.?|\\#)\\d{3,4})?$"

    private val phoneNumberConstraint: Constraint[String] = Constraint[String] { input: String =>
      if (input.trim.isEmpty) Invalid(ValidationError("error.contact.phone.empty"))
      else if (!input.trim.matches(ukPhoneNumberRegex)) Invalid(ValidationError("error.contact.phone.invalid"))
      else Valid
    }

    private val phoneNumberMapping = text.verifying(phoneNumberConstraint)

    val contactTelephoneForm = Form[String](
      single(
        "telephone" -> phoneNumberMapping
      )
    )
  }
}
