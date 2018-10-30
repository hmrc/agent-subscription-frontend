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

package uk.gov.hmrc.agentsubscriptionfrontend.models

import uk.gov.hmrc.http.BadRequestException

case class BusinessType(businessType: IdentifyBusinessType)

//val validBusinessTypes = Seq("sole_trader", "limited_company", "partnership", "llp")

sealed trait IdentifyBusinessType {
  val key: String = this match {
    case IdentifyBusinessType.SoleTrader     => "sole_trader"
    case IdentifyBusinessType.LimitedCompany => "limited_company"
    case IdentifyBusinessType.Partnership    => "partnership"
    case IdentifyBusinessType.Llp            => "llp"
  }
}

object IdentifyBusinessType {

  case object SoleTrader extends IdentifyBusinessType
  case object LimitedCompany extends IdentifyBusinessType
  case object Partnership extends IdentifyBusinessType
  case object Llp extends IdentifyBusinessType

  def apply(convertToType: String): IdentifyBusinessType = convertToType match {
    case "sole_trader"     => IdentifyBusinessType.SoleTrader
    case "limited_company" => IdentifyBusinessType.LimitedCompany
    case "partnership"     => IdentifyBusinessType.Partnership
    case "llp"             => IdentifyBusinessType.Llp
    case _                 => throw new BadRequestException("Submitted form value did not contain valid businessType identifier")
  }
}
