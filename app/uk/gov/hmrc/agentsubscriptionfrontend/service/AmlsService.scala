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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import play.api.Logging
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentSubscriptionConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.FormBundleStatus.{Approved, ApprovedWithConditions, FormBundleStatus, Pending}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AMLSForm, AmlsSubscriptionRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.service.AmlsValidationResult._
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AmlsService @Inject()(agentSubscriptionConnector: AgentSubscriptionConnector) extends Logging {

  def validateAmlsSubscription(amlsForm: AMLSForm)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AmlsValidationResult] =
    if (amlsForm.amlsCode != "HMRC") Future successful ResultOK(None)
    else {
      checkAmlsNumber(amlsForm.membershipNumber, Some(amlsForm.expiry))
    }

  def checkEndDateMatch(amlsRecord: AmlsSubscriptionRecord, expireDate: LocalDate): AmlsValidationResult =
    amlsRecord.currentRegYearEndDate.map(_ == expireDate) match {
      case Some(false) | None => DateNotMatched
      case Some(true)         => ResultOK(Some(amlsRecord.safeId))
    }

  def checkAmlsNumber(membershipNumber: String, maybeExpireDate: Option[LocalDate])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[AmlsValidationResult] =
    agentSubscriptionConnector.getAmlsSubscriptionRecord(membershipNumber).map {
      case Some(amlsRecord) =>
        if (amlsRecord.suspended.contains(true)) AmlsSuspended
        else
          amlsRecord.formBundleStatus match {
            case Pending => ResultOK(Some(amlsRecord.safeId))
            case Approved | ApprovedWithConditions =>
              maybeExpireDate match {
                case Some(expireDate) => checkEndDateMatch(amlsRecord, expireDate)
                case None             => ResultOKButCheckDate(Some(amlsRecord.safeId))
              }

            case status => {
              logger.warn(s"amls record returned an ineligible status $status")
              AmlsCheckFailed(status)
            }
          }
      case None => RecordNotFound
    }

  def checkAmlsExpiryDate(membershipNumber: String, expireDate: LocalDate)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[AmlsValidationResult] =
    agentSubscriptionConnector.getAmlsSubscriptionRecord(membershipNumber).map {
      case Some(amlsRecord) =>
        checkEndDateMatch(amlsRecord, expireDate)
      case None => RecordNotFound
    }

}

object AmlsValidationResult {

  sealed trait AmlsValidationResult

  case class ResultOK(amlsSafeId: Option[String]) extends AmlsValidationResult

  case class ResultOKButCheckDate(amlsSafeId: Option[String]) extends AmlsValidationResult

  case object RecordNotFound extends AmlsValidationResult

  case object DateNotMatched extends AmlsValidationResult

  case object AmlsSuspended extends AmlsValidationResult

  case class AmlsCheckFailed(formBundleStatus: FormBundleStatus) extends AmlsValidationResult
}
