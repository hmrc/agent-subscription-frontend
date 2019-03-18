/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.LocalDate

import play.api.data.FormError
import play.api.data.format.Formatter

import scala.util.{Failure, Success, Try}

object DateFieldHelper {

  val dateOfBirthFormatter: Formatter[LocalDate] = new Formatter[LocalDate] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], LocalDate] =
      (data.get("dob.day").map(_.trim), data.get("dob.month").map(_.trim), data.get("dob.year").map(_.trim)) match {
        case (Some(day), Some(month), Some(year)) =>
          if (day.isEmpty && month.isEmpty && year.isEmpty) {
            Left(Seq(FormError("dob", "date-of-birth.blank")))
          } else {
            if (day.isEmpty && month.isEmpty)
              Left(Seq(FormError("dob", "date-of-birth.day-month.empty")))
            else if (month.isEmpty && year.isEmpty)
              Left(Seq(FormError("dob", "date-of-birth.month-year.empty")))
            else if (day.isEmpty && year.isEmpty)
              Left(Seq(FormError("dob", "date-of-birth.day-year.empty")))
            else if (day.isEmpty)
              Left(Seq(FormError("dob", "date-of-birth.day.empty")))
            else if (month.isEmpty)
              Left(Seq(FormError("dob", "date-of-birth.month.empty")))
            else if (year.isEmpty)
              Left(Seq(FormError("dob", "date-of-birth.year.empty")))
            else if (!validDay(day))
              Left(Seq(FormError("dob", "date-of-birth.day.invalid")))
            else if (!validMonth(month))
              Left(Seq(FormError("dob", "date-of-birth.month.invalid")))
            else if (!validYear(year))
              Left(Seq(FormError("dob", "date-of-birth.year.invalid")))
            else {
              val date = LocalDate.of(year.toInt, month.toInt, day.toInt)
              if (date.isAfter(LocalDate.now())) {
                Left(Seq(FormError("dob", "date-of-birth.must.be.past")))
              } else if (date.isBefore(LocalDate.now().withYear(1900))) {
                Left(Seq(FormError("dob", "date-of-birth.is.not.real")))
              }

              else {
                Right(date)
              }
            }

          }
      }

    override def unbind(key: String, value: LocalDate): Map[String, String] =
      Map(
        "dob.day"   -> value.getDayOfMonth.toString,
        "dob.month" -> value.getMonthValue.toString,
        "dob.year"  -> value.getYear.toString
      )
  }

  private def validDay(day: String) =
    Try(day.toInt) match {
      case Success(p) =>
        if (p < 1 || p > 31) {
          false
        } else {
          true
        }
      case Failure(_) => false
    }

  private def validMonth(month: String) =
    Try(month.toInt) match {
      case Success(p) =>
        if (p < 1 || p > 12) {
          false
        } else {
          true
        }
      case Failure(_) => false
    }

  private def validYear(year: String) =
    Try(year.toInt) match {
      case Success(p) =>
        if (p < 1900) {
          false
        } else {
          true
        }
      case Failure(_) => false
    }
}
