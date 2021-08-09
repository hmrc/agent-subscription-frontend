/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import play.api.Logger.logger
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentSubscriptionConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.FormBundleStatus.{Approved, ApprovedWithConditions, FormBundleStatus, Pending}
import uk.gov.hmrc.agentsubscriptionfrontend.models.AMLSForm
import uk.gov.hmrc.agentsubscriptionfrontend.service.AmlsValidationResult._
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AmlsService @Inject()(agentSubscriptionConnector: AgentSubscriptionConnector) {

  def validateAmlsSubscription(amlsForm: AMLSForm)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AmlsValidationResult] =
    if (amlsForm.amlsCode != "HMRC") Future successful ResultOK
    else {
      agentSubscriptionConnector.getAmlsSubscriptionRecord(amlsForm.membershipNumber).map {
        case Some(amlsRecord) =>
          if (amlsRecord.suspended.contains(true)) AmlsSuspended
          else
            amlsRecord.formBundleStatus match {
              case Pending => ResultOK
              case Approved | ApprovedWithConditions =>
                amlsRecord.currentRegYearEndDate.map(_ == amlsForm.expiry) match {
                  case Some(false) | None => DateNotMatched
                  case Some(true)         => ResultOK
                }
              case status => {
                logger.warn(s"amls record returned an ineligible status $status")
                AmlsCheckFailed(status)
              }
            }
        case None => RecordNotFound
      }
    }
}

object AmlsValidationResult {

  sealed trait AmlsValidationResult

  case object ResultOK extends AmlsValidationResult

  case object RecordNotFound extends AmlsValidationResult

  case object DateNotMatched extends AmlsValidationResult

  case object AmlsSuspended extends AmlsValidationResult

  case class AmlsCheckFailed(formBundleStatus: FormBundleStatus) extends AmlsValidationResult
}
