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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import javax.inject.{ Inject, Singleton }
import play.api.Logger
import play.api.mvc.{ AnyContent, Request }
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AuditService
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.domain.{ Nino, SaAgentReference, TaxIdentifier }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AssuranceService @Inject() (
  appConfig: AppConfig,
  assuranceConnector: AgentAssuranceConnector,
  auditService: AuditService,
  sessionStoreService: SessionStoreService) {

  def assureIsAgent(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AssuranceResults]] = {
    if (appConfig.agentAssuranceFlag) {
      val futureManuallyAssured = assuranceConnector.isManuallyAssuredAgent(utr)
      val futureR2dw = assuranceConnector.isR2DWAgent(utr)

      for {
        isOnRefusalToDealWithList <- futureR2dw
        isManuallyAssured <- futureManuallyAssured
        assuranceResults <- if (isManuallyAssured || isOnRefusalToDealWithList) {
          Future.successful(Some(AssuranceResults(isOnRefusalToDealWithList, isManuallyAssured, None, None)))
        } else {
          val futurePaye = assuranceConnector.hasAcceptableNumberOfPayeClients
          val futureSA = assuranceConnector.hasAcceptableNumberOfSAClients

          for {
            hasAcceptableNumberOfPayeClients <- futurePaye
            hasAcceptableNumberOfSAClients <- futureSA
          } yield Some(AssuranceResults(isOnRefusalToDealWithList, isManuallyAssured, Some(hasAcceptableNumberOfPayeClients), Some(hasAcceptableNumberOfSAClients)))
        }
      } yield assuranceResults
    } else {
      Future.successful(None)
    }
  }

  def checkActiveCesaRelationship(userEnteredNinoOrUtr: TaxIdentifier, name: String,
    saAgentReference: SaAgentReference)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[AnyContent],
    agent: Agent): Future[Boolean] = {
    assuranceConnector.hasActiveCesaRelationship(userEnteredNinoOrUtr, name, saAgentReference).map { relationshipExists =>
      val (userEnteredNino, userEnteredUtr) = userEnteredNinoOrUtr match {
        case nino @ Nino(_) => (Some(nino), None)
        case utr @ Utr(_) => (None, Some(utr))
      }

      for {
        knownFactResultOpt <- sessionStoreService.fetchKnownFactsResult
        _ <- knownFactResultOpt match {
          case Some(knownFactsResult) =>
            auditService.sendAgentAssuranceAuditEvent(
              knownFactsResult,
              AssuranceResults(false, false, Some(false), Some(false)),
              Some(AssuranceCheckInput(Some(relationshipExists), Some(saAgentReference.value), userEnteredUtr, userEnteredNino)))
          case None =>
            Future.successful(Logger.warn("Could not send audit events due to empty knownfacts results"))
        }
      } yield ()

      relationshipExists
    }
  }
}
