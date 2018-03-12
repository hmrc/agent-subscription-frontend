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

import javax.inject.{Inject, Named, Singleton}

import play.api.Logger
import play.api.mvc.AnyContent
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AuditService
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AgentRequest
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.domain.{Nino, SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AssuranceService @Inject()(@Named("agentAssuranceFlag") agentAssuranceFlag: Boolean,
                                 agentAssuranceConnector: AgentAssuranceConnector,
                                 auditService: AuditService,
                                 sessionStoreService: SessionStoreService) {

  def assureIsAgent(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AssuranceResults]] = {
    if (agentAssuranceFlag) {
      agentAssuranceConnector.isManuallyAssuredAgent(utr).flatMap { isManuallyAssured =>
        if(isManuallyAssured)
          Future.successful(None)
        else {
          val futurePaye = agentAssuranceConnector.hasAcceptableNumberOfPayeClients
          val futureSA = agentAssuranceConnector.hasAcceptableNumberOfSAClients

          for {
            hasAcceptableNumberOfPayeClients <- futurePaye
            hasAcceptableNumberOfSAClients <- futureSA
          } yield Some(AssuranceResults(hasAcceptableNumberOfPayeClients, hasAcceptableNumberOfSAClients))
        }
      }
    }
    else Future.successful(None)
  }

  def isOnRefusalToDealWithList(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = {
    if(agentAssuranceFlag)
      agentAssuranceConnector.isR2DWAgent(utr).map(Some(_))
    else
      Future.successful(None)
  }

  def checkActiveCesaRelationship(userEnteredNinoOrUtr: TaxIdentifier, name: String,
                                  saAgentReference: SaAgentReference)
                                 (implicit hc: HeaderCarrier,
                                  ec: ExecutionContext,
                                  request: AgentRequest[AnyContent], authContext: AuthContext): Future[Boolean] = {
    agentAssuranceConnector.hasActiveCesaRelationship(userEnteredNinoOrUtr, name, saAgentReference).map { relationshipExists =>
      val (userEnteredNino, userEnteredUtr) = userEnteredNinoOrUtr match {
        case nino @ Nino(_) => (Some(nino), None)
        case utr @ Utr(_) => (None, Some(utr))
      }

      for {
        knownFactResultOpt <- sessionStoreService.fetchKnownFactsResult
        _ <- knownFactResultOpt match {
          case Some(knownFactsResult) =>
            auditService.sendAgentAssuranceAuditEvent(knownFactsResult,
              AssuranceResults(false, false),
              Some(AssuranceCheckInput(Some(relationshipExists), Some(saAgentReference.value), userEnteredUtr, userEnteredNino)))
          case None =>
            Future.successful(Logger.warn("Could not send audit events due to empty knownfacts results"))
        }
      } yield ()

      relationshipExists
    }
  }
}
