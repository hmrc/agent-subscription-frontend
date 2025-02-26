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

package uk.gov.hmrc.agentsubscriptionfrontend.validators
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation._
import play.api.data.{FormError, Mapping}
import play.api.i18n.Messages
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.denylistedPostcodes.PostcodesLoader
import uk.gov.hmrc.domain.Nino

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object CommonValidators {
  private val DesPostcodeRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$"
  private val PostcodeSpecialCharsRegex = """^[A-Za-z0-9 ]*$"""
  private val EmailLocalPartRegex = """^[a-zA-Z0-9\.!#$%&'*+\/=?^_`{|}~-]+(?<!\.)$"""
  private val EmailDomainRegex =
    """[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"""
  private val DesTextRegex = "^[A-Za-z0-9 \\-,.&'\\/]*$"

  private type UtrErrors = (String, String, String)
  private val DefaultUtrErrors = ("error.utr.blank", "error.utr.invalid", "error.utr.incorrectFormat")

  private val EmailMaxLength = 132
  private val PostcodeMaxLength = 8
  private val AddresslineMaxLength = 35
  private val BusinessNameMaxLength = 40
  private val UtrMaxLength = 10
  private val SaAgentCodeMaxLength = 6
  private val crnLength = 8
  private val crnRegex = "[A-Z]{2}[0-9]{6}|[0-9]{8}"

  def saAgentCode: Mapping[String] = text verifying saAgentCodeConstraint

  def utr: Mapping[String] = text verifying utrConstraint()

  def clientDetailsUtr: Mapping[String] =
    text verifying utrConstraint(("error.client.sautr.blank", "error.client.sautr.invalid", "error.client.sautr.incorrectFormat"))

  def businessUtr(businessType: String): Mapping[String] = {
    val utrErrors = businessType match {
      case "sole_trader" =>
        ("error.sautr.blank", "error.sautr.invalid", "error.sautr.incorrectFormat")
      case "limited_company" =>
        ("error.companyutr.blank", "error.companyutr.invalid", "error.companyutr.incorrectFormat")
      case "partnership" =>
        ("error.partnershiputr.blank", "error.partnershiputr.invalid", "error.partnershiputr.incorrectFormat")
      case "llp" =>
        ("error.llputr.blank", "error.llputr.invalid", "error.llputr.incorrectFormat")
      case _ =>
        DefaultUtrErrors
    }

    text verifying utrConstraint(utrErrors)
  }

  def clientDetailsNino: Mapping[String] = text verifying ninoConstraint

  def postcode: Mapping[String] =
    of[String](stringFormatWithMessage("error.postcode.empty")) verifying nonEmptyPostcode

  def postcodeWithDenylist(denylistedPostcodes: Set[String]): Mapping[String] =
    postcode
      .verifying("error.postcode.denylisted", x => validateDenylist(x, denylistedPostcodes))

  def crn: Mapping[String] = text verifying crnConstraint

  def emailAddress: Mapping[String] =
    text
      .verifying(validEmailAddress(s"error.business-email.empty", "error.business-email.maxlength", "error.business-email.format"))

  def contactEmailAddress: Mapping[String] =
    text.verifying(validEmailAddress())

  def businessName: Mapping[String] =
    text
      .verifying(maxLength(BusinessNameMaxLength, "error.business-name.maxlength"))
      .verifying(
        checkOneAtATime(
          noAmpersand("error.business-name.invalid"),
          desText(msgKeyRequired = "error.business-name.empty", msgKeyInvalid = "error.business-name.invalid")
        )
      )

  def tradingName: Mapping[String] =
    text
      .verifying(maxLength(BusinessNameMaxLength, "error.trading-name-name.maxlength"))
      .verifying(
        checkOneAtATime(
          noAmpersand("error.trading-name.invalid"),
          desText(msgKeyRequired = "error.trading-name.empty", msgKeyInvalid = "error.trading-name.invalid")
        )
      )

  def amlsCode(bodies: Set[String]): Mapping[String] = text verifying amlsCodeConstraint(bodies)

  def membershipNumber: Mapping[String] = nonEmptyTextWithMsg("error.moneyLaunderingCompliance.membershipNumber.empty")

  def expiryDate: Mapping[LocalDate] =
    tuple(
      "year"  -> text.verifying("year", y => y.trim.nonEmpty || y.matches("^[0-9]{1,4}$")),
      "month" -> text.verifying("month", y => y.trim.nonEmpty || y.matches("^[0-9]{1,2}$")),
      "day"   -> text.verifying("day", d => d.trim.nonEmpty || d.matches("^[0-9]{1,2}$"))
    ).verifying(checkOneAtATime(Seq(invalidDateConstraint, pastExpiryDateConstraint, within13MonthsExpiryDateConstraint)))
      .transform(
        { case (y, m, d) => LocalDate.of(y.trim.toInt, m.trim.toInt, d.trim.toInt) },
        (date: LocalDate) => (date.getYear.toString, date.getMonthValue.toString, date.getDayOfMonth.toString)
      )

  def renewalDate: Mapping[LocalDate] =
    tuple(
      "year"  -> text.verifying("year", y => y.trim.nonEmpty || y.matches("^[0-9]{1,4}$")),
      "month" -> text.verifying("month", y => y.trim.nonEmpty || y.matches("^[0-9]{1,2}$")),
      "day"   -> text.verifying("day", d => d.trim.nonEmpty || d.matches("^[0-9]{1,2}$"))
    ).verifying(checkOneAtATime(Seq(invalidDateConstraint, pastExpiryDateConstraint, within13MonthsExpiryDateConstraint)))
      .transform(
        { case (y, m, d) => LocalDate.of(y.trim.toInt, m.trim.toInt, d.trim.toInt) },
        (date: LocalDate) => (date.getYear.toString, date.getMonthValue.toString, date.getDayOfMonth.toString)
      )

  def appliedOnDate(implicit messages: Messages): Mapping[LocalDate] =
    tuple(
      "year"  -> text.verifying("year", y => y.trim.nonEmpty || y.matches("^[0-9]{1,4}$")),
      "month" -> text.verifying("month", y => y.trim.nonEmpty || y.matches("^[0-9]{1,2}$")),
      "day"   -> text.verifying("day", d => d.trim.nonEmpty || d.matches("^[0-9]{1,2}$"))
    ).verifying(checkOneAtATime(Seq(invalidDateConstraint, within6MonthsPastDateConstraint, futureApplicationConstraint)))
      .transform(
        { case (y, m, d) => LocalDate.of(y.trim.toInt, m.trim.toInt, d.trim.toInt) },
        (date: LocalDate) => (date.getYear.toString, date.getMonthValue.toString, date.getDayOfMonth.toString)
      )

  def addressLine1: Mapping[String] =
    text
      .verifying(maxLength(AddresslineMaxLength, "error.addressline.1.maxlength"))
      .verifying(desText(msgKeyRequired = "error.addressline.1.empty", msgKeyInvalid = "error.addressline.1.invalid"))

  def addressLine234(lineNumber: Int): Mapping[Option[String]] =
    optional(
      text
        .verifying(maxLength(AddresslineMaxLength, s"error.addressline.$lineNumber.maxlength"))
        .verifying(desText(msgKeyRequired = s"error.addressline.$lineNumber.empty", msgKeyInvalid = s"error.addressline.$lineNumber.invalid"))
    )

  def radioInputSelected[T](message: String = "error.no-radio-selected"): Constraint[Option[T]] =
    Constraint[Option[T]] { fieldValue: Option[T] =>
      if (fieldValue.isDefined)
        Valid
      else
        Invalid(ValidationError(message))
    }

  def radioInputStringSelected(message: String = "error.no-radio-selected"): Constraint[String] =
    Constraint[String] { fieldValue: String =>
      if (fieldValue.nonEmpty)
        Valid
      else
        Invalid(ValidationError(message))
    }

  def nonEmptyTextWithMsg(errorMessageKey: String): Mapping[String] =
    text verifying nonEmptyWithMessage(errorMessageKey)

  // Same as play.api.data.validation.Constraints.nonEmpty but with a custom message instead of error.required
  private def nonEmptyWithMessage(messageKey: String): Constraint[String] = Constraint[String] { (o: String) =>
    if (o == null) Invalid(ValidationError(messageKey))
    else if (o.trim.isEmpty) Invalid(ValidationError(messageKey))
    else Valid
  }

  // Same as play.api.data.validation.Constraints.maxLength but with a chance to use a custom message instead of error.maxLength
  private def maxLength(length: Int, messageKey: String): Constraint[String] =
    Constraint[String]("constraint.maxLength", length) { o =>
      require(length >= 0, "string maxLength must not be negative")
      if (o == null) Invalid(ValidationError(messageKey, length))
      else if (o.length <= length) Valid
      else Invalid(ValidationError(messageKey, length))
    }

  // Same as play.api.data.format.Formats.stringFormat but with a custom message instead of error.required
  private def stringFormatWithMessage(messageKey: String): Formatter[String] = new Formatter[String] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = data.get(key).toRight(Seq(FormError(key, messageKey, Nil)))

    def unbind(key: String, value: String) = Map(key -> value)
  }

  private def validEmailAddress(
    nonEmptyMessageKey: String = "error.contact-email.empty",
    maxLengthMessageKey: String = "error.contact-email.maxLength",
    wrongFormatMessageKey: String = "error.contact-email.format"
  ) = Constraint { fieldValue: String =>
    nonEmptyWithMessage(nonEmptyMessageKey)(fieldValue) match {
      case i: Invalid => i
      case Valid =>
        if (fieldValue.length > EmailMaxLength) {
          Invalid(ValidationError(maxLengthMessageKey))
        } else if (fieldValue.contains('@')) {
          val email = fieldValue.split('@')
          if (!email(0).matches(EmailLocalPartRegex) || !email(1).matches(EmailDomainRegex)) {
            Invalid(ValidationError(wrongFormatMessageKey))
          } else Constraints.emailAddress(fieldValue)
        } else
          Invalid(ValidationError(wrongFormatMessageKey))
    }
  }

  private val nonEmptyPostcode: Constraint[String] = Constraint[String] { fieldValue: String =>
    nonEmptyWithMessage("error.postcode.empty")(fieldValue) match {
      case i: Invalid =>
        i
      case Valid =>
        fieldValue match {
          case value if value.length > PostcodeMaxLength => Invalid(ValidationError("error.postcode.maxlength"))
          case value if !value.matches(PostcodeSpecialCharsRegex) =>
            Invalid(ValidationError("error.postcode.invalidchars"))
          case value if !value.matches(DesPostcodeRegex) => Invalid(ValidationError("error.postcode.invalid"))
          case _                                         => Valid
        }
    }
  }

  private val crnConstraint: Constraint[String] = Constraint[String] { fieldValue: String =>
    nonEmptyWithMessage("error.crn.empty")(fieldValue) match {
      case i: Invalid => i
      case Valid =>
        fieldValue match {
          case value if value.length != crnLength || !value.matches(crnRegex) =>
            Invalid(ValidationError("error.crn.invalid"))
          case _ => Valid
        }
    }
  }

  private def noAmpersand(errorMsgKey: String) = Constraints.pattern("[^&]*".r, error = errorMsgKey)

  private[validators] def desText(msgKeyRequired: String, msgKeyInvalid: String): Constraint[String] =
    Constraint[String] { fieldValue: String =>
      nonEmptyWithMessage(msgKeyRequired)(fieldValue) match {
        case i: Invalid => i
        case Valid =>
          fieldValue match {
            case value if !value.matches(DesTextRegex) => Invalid(ValidationError(msgKeyInvalid))
            case _                                     => Valid
          }
      }
    }

  def validateDenylist(postcode: String, denylistedPostcodes: Set[String]): Boolean =
    PostcodesLoader.formatPostcode(postcode) match {
      case Some(p) => !denylistedPostcodes.contains(p)
      case None    => false
    }

  private val saAgentCodeConstraint: Constraint[String] = Constraint[String] { fieldValue: String =>
    val formattedCode = fieldValue.replace(" ", "")

    if (formattedCode.isEmpty)
      Invalid(ValidationError("error.saAgentCode.blank"))
    else if (!formattedCode.matches("""^[a-zA-Z0-9]*$"""))
      Invalid(ValidationError("error.saAgentCode.invalid"))
    else if (formattedCode.length != SaAgentCodeMaxLength)
      Invalid(ValidationError("error.saAgentCode.length"))
    else
      Valid
  }

  private def checkOneAtATime[T](firstConstraint: Constraint[T], secondConstraint: Constraint[T]) = Constraint[T] { fieldValue: T =>
    firstConstraint(fieldValue) match {
      case i @ Invalid(_) => i
      case Valid          => secondConstraint(fieldValue)
    }
  }

  def checkOneAtATime[A](constraints: Seq[Constraint[A]]): Constraint[A] = Constraint[A] { fieldValue: A =>
    @tailrec
    def loop(c: List[Constraint[A]]): ValidationResult =
      c match {
        case Nil => Valid
        case head :: tail =>
          head(fieldValue) match {
            case i @ Invalid(_) => i
            case Valid          => loop(tail)
          }
      }

    loop(constraints.toList)
  }

  private def utrConstraint(errorMessages: UtrErrors = DefaultUtrErrors): Constraint[String] = Constraint[String] { fieldValue: String =>
    val formattedField = fieldValue.replace(" ", "")
    val (blank, invalid, incorrectFormat) = errorMessages

    def isNumber(str: String): Boolean = str.map(_.isDigit).reduceOption(_ && _).getOrElse(false)

    Constraints.nonEmpty.apply(formattedField) match {
      case _: Invalid => Invalid(ValidationError(blank))
      case _ if !isNumber(formattedField) || formattedField.length != UtrMaxLength =>
        Invalid(ValidationError(incorrectFormat))
      case _ if !Utr.isValid(formattedField) =>
        Invalid(ValidationError(invalid))
      case _ => Valid
    }
  }

  val ninoConstraint: Constraint[String] = Constraint[String] { fieldValue: String =>
    val formattedField = fieldValue.replaceAll("\\s", "").toUpperCase

    Constraints.nonEmpty.apply(formattedField) match {
      case _: Invalid                         => Invalid(ValidationError("error.nino.empty"))
      case _ if !Nino.isValid(formattedField) => Invalid(ValidationError("error.nino.invalid"))
      case _                                  => Valid
    }
  }

  private def amlsCodeConstraint(bodies: Set[String]): Constraint[String] = Constraint[String] { fieldValue: String =>
    Constraints.nonEmpty.apply(fieldValue) match {
      case _: Invalid => Invalid(ValidationError("error.moneyLaunderingCompliance.amlscode.empty"))
      case _ if !validateAMLSBodies(fieldValue, bodies) =>
        Invalid(ValidationError("error.moneyLaunderingCompliance.amlscode.invalid"))
      case _ => Valid
    }
  }

  private val invalidDateConstraint: Constraint[(String, String, String)] = Constraint[(String, String, String)] { data: (String, String, String) =>
    val (year, month, day) = data

    Try {
      require(year.length == 4, "Year must be 4 digits")
      LocalDate.of(year.toInt, month.toInt, day.toInt)
    } match {
      case Failure(_) => Invalid(ValidationError("error.moneyLaunderingCompliance.date.invalid"))
      case Success(_) => Valid
    }
  }

  private val pastExpiryDateConstraint: Constraint[(String, String, String)] = Constraint[(String, String, String)] {
    data: (String, String, String) =>
      val (year, month, day) = data

      if (LocalDate.of(year.toInt, month.toInt, day.toInt).isAfter(LocalDate.now()))
        Valid
      else
        Invalid(ValidationError("error.moneyLaunderingCompliance.date.past"))
  }

  private val within13MonthsExpiryDateConstraint: Constraint[(String, String, String)] =
    Constraint[(String, String, String)] { data: (String, String, String) =>
      val (year, month, day) = data

      val futureDate = LocalDate.now().plusMonths(13)

      if (LocalDate.of(year.toInt, month.toInt, day.toInt).isBefore(futureDate))
        Valid
      else
        Invalid(ValidationError("error.moneyLaunderingCompliance.date.before"))
    }

  private def within6MonthsPastDateConstraint(implicit messages: Messages): Constraint[(String, String, String)] =
    Constraint[(String, String, String)] { data: (String, String, String) =>
      val (year, month, day) = data

//      Needs to include todays date, 6 months ago
      val sixMonthsEarlier = LocalDate.now().minusMonths(6).minusDays(1)

      if (LocalDate.of(year.toInt, month.toInt, day.toInt).isAfter(sixMonthsEarlier))
        Valid
      else
        Invalid(
          ValidationError(
            Messages("error.amls.enter-renewal-date.date.too-old", sixMonthsEarlier.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")))
          )
        )
    }

  private def validateAMLSBodies(amlsCode: String, bodies: Set[String]): Boolean =
    bodies.contains(amlsCode)

  private val futureApplicationConstraint: Constraint[(String, String, String)] = Constraint[(String, String, String)] {
    data: (String, String, String) =>
      val (year, month, day) = data

      if (LocalDate.of(year.toInt, month.toInt, day.toInt).isAfter(LocalDate.now()))
        Invalid(ValidationError("error.amls.enter-renewal-date.date.cannot-be-in-future"))
      else
        Valid
  }
}
