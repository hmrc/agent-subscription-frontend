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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import play.api.Logger
import play.api.mvc.{AnyContent, Request, RequestHeader}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AuditService
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.util.valueOps
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.domain.{Nino, SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

//noinspection ScalaStyle
@Singleton
class AssuranceService @Inject() (
  appConfig: AppConfig,
  assuranceConnector: AgentAssuranceConnector,
  auditService: AuditService,
  sessionStoreService: MongoDBSessionStoreService
) {

  def assureIsAgent(utr: String)(implicit rh: RequestHeader, ec: ExecutionContext): Future[Option[AssuranceResults]] =
    if (appConfig.agentAssuranceRun) {
      for {
        agentChecksResponse <- assuranceConnector.getUtrDetails(utr)
        assuranceResults <- if (agentChecksResponse.isRefusalToDealWith || agentChecksResponse.isManuallyAssured) {
                              Future.successful(
                                Some(
                                  AssuranceResults(
                                    isOnRefusalToDealWithList = agentChecksResponse.isRefusalToDealWith,
                                    isManuallyAssured = agentChecksResponse.isManuallyAssured,
                                    hasAcceptableNumberOfPayeClients = None,
                                    hasAcceptableNumberOfSAClients = None,
                                    hasAcceptableNumberOfVatDecOrgClients = None,
                                    hasAcceptableNumberOfIRCTClients = None
                                  )
                                )
                              )
                            } else {

                              for {
                                hasAcceptableNumberOfPayeClientsOpt <- assuranceConnector.hasAcceptableNumberOfPayeClients
                                                                         .map(Some(_))
                                hasAcceptableNumberOfSAClientsOpt <- assuranceConnector.hasAcceptableNumberOfSAClients
                                                                       .map(Some(_))
                                hasAcceptableNumberOfVatDecOrgClientsOpt <- assuranceConnector.hasAcceptableNumberOfVatDecOrgClients
                                                                              .map(Some(_))
                                hasAcceptableNumberOfIRCTClientsOpt <- assuranceConnector.hasAcceptableNumberOfIRCTClients
                                                                         .map(Some(_))
                              } yield Some(
                                AssuranceResults(
                                  isOnRefusalToDealWithList = agentChecksResponse.isRefusalToDealWith,
                                  isManuallyAssured = agentChecksResponse.isManuallyAssured,
                                  hasAcceptableNumberOfPayeClients = hasAcceptableNumberOfPayeClientsOpt,
                                  hasAcceptableNumberOfSAClients = hasAcceptableNumberOfSAClientsOpt,
                                  hasAcceptableNumberOfVatDecOrgClients = hasAcceptableNumberOfVatDecOrgClientsOpt,
                                  hasAcceptableNumberOfIRCTClients = hasAcceptableNumberOfIRCTClientsOpt
                                )
                              )
                            }
      } yield assuranceResults
    } else {
      Future.successful(None)
    }

  def checkActiveCesaRelationship(userEnteredNinoOrUtr: TaxIdentifier, name: String, saAgentReference: SaAgentReference)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[AnyContent],
    agent: Agent,
    crypto: Encrypter with Decrypter
  ): Future[Boolean] =
    assuranceConnector.hasActiveCesaRelationship(userEnteredNinoOrUtr, name, saAgentReference).map { relationshipExists =>
      val (userEnteredNino, userEnteredUtr) = userEnteredNinoOrUtr match {
        case nino @ Nino(_) => (Some(nino), None)
        case utr @ Utr(_)   => (None, Some(utr))
        case _              => throw new IllegalArgumentException("unexpected tax identifier type: " + userEnteredNinoOrUtr)
      }

      for {
        agentSessionOpt <- sessionStoreService.fetchAgentSession
        _ <- agentSessionOpt match {
               case Some(agentSession) =>
                 (agentSession.utr, agentSession.postcode) match {
                   case (Some(utr), Some(postcode)) =>
                     auditService.sendAgentAssuranceAuditEvent(
                       utr = utr,
                       postcode = postcode,
                       assuranceResults = AssuranceResults(
                         isOnRefusalToDealWithList = false,
                         isManuallyAssured = false,
                         hasAcceptableNumberOfPayeClients = Some(false),
                         hasAcceptableNumberOfSAClients = Some(false),
                         hasAcceptableNumberOfVatDecOrgClients = Some(false),
                         hasAcceptableNumberOfIRCTClients = Some(false)
                       ),
                       assuranceCheckInput = Some(
                         AssuranceCheckInput(
                           passCesaAgentAssuranceCheck = Some(relationshipExists),
                           userEnteredSaAgentRef = Some(saAgentReference.value),
                           userEnteredUtr = userEnteredUtr,
                           userEnteredNino = userEnteredNino
                         )
                       )
                     )
                   case _ => ().toFuture
                 }

               case None =>
                 Future.successful(Logger(getClass).warn("Could not send audit events due to empty knownfacts results"))
             }
      } yield ()

      relationshipExists
    }
}
