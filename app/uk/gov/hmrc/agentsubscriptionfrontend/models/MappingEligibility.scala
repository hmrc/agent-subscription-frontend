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

package uk.gov.hmrc.agentsubscriptionfrontend.models

sealed abstract class MappingEligibility(val isEligible: Option[Boolean])

object MappingEligibility {
  case object IsEligible extends MappingEligibility(isEligible = Some(true))
  case object IsNotEligible extends MappingEligibility(isEligible = Some(false))
  case object UnknownEligibility extends MappingEligibility(isEligible = None)

  def apply(eligibility: Option[Boolean]): MappingEligibility =
    eligibility match {
      case Some(true)  => IsEligible
      case Some(false) => IsNotEligible
      case None        => UnknownEligibility
    }
}
