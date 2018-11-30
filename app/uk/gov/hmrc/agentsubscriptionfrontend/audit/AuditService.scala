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

package uk.gov.hmrc.agentsubscriptionfrontend.audit

import java.util.concurrent.ConcurrentHashMap

import javax.inject.Inject
import com.google.inject.Singleton
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AssuranceCheckInput, AssuranceResults, KnownFactsResult}
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.collection.JavaConversions
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object AgentSubscriptionFrontendEvent extends Enumeration {
  val AgentAssurance = Value
  type AgentSubscriptionFrontendEvent = Value
}

class AuditData {

  private val details = new ConcurrentHashMap[String, Any]

  def set(key: String, value: Any): AuditData = {
    details.put(key, value)
    this
  }

  private[audit] def getDetails: Map[String, Any] =
    JavaConversions.mapAsScalaMap(details).toMap

}

@Singleton
class AuditService @Inject()(val auditConnector: AuditConnector) {

  import AgentSubscriptionFrontendEvent._

  val agentAssuranceDetailsFields: Seq[(String, Option[Any])] = Seq(
    ("utr", None),
    ("postcode", None),
    ("refuseToDealWith", None),
    ("isEnrolledSAAgent", None),
    ("saAgentRef", None),
    ("passSaAgentAssuranceCheck", None),
    ("isEnrolledPAYEAgent", None),
    ("payeAgentRef", None),
    ("passPayeAgentAssuranceCheck", None),
    ("userEnteredSaAgentRef", None),
    ("userEnteredUtr", None),
    ("userEnteredNino", None),
    ("passCESAAgentAssuranceCheck", None),
    ("passVatDecOrgAgentAssuranceCheck", None),
    ("authProviderId", None),
    ("authProviderType", None)
  )

  def sendAgentAssuranceAuditEvent(
    knownFactsResult: KnownFactsResult,
    assuranceResults: AssuranceResults,
    assuranceCheckInput: Option[AssuranceCheckInput] = None)(
    implicit request: Request[AnyContent],
    agent: Agent,
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Unit] = {
    implicit val auditData: AuditData = new AuditData

    auditData
      .set("utr", knownFactsResult.utr)
      .set("postcode", knownFactsResult.postcode)

    assuranceResults.hasAcceptableNumberOfSAClients.foreach(auditData.set("passSaAgentAssuranceCheck", _))
    assuranceResults.hasAcceptableNumberOfPayeClients.foreach(auditData.set("passPayeAgentAssuranceCheck", _))
    assuranceResults.hasAcceptableNumberOfVatDecOrgClients.foreach(auditData.set("passVatDecOrgAgentAssuranceCheck", _))

    val payeEnrolmentOpt = agent.hasIRPAYEAGENT
    auditData.set("isEnrolledPAYEAgent", payeEnrolmentOpt.isDefined)

    val saEnrolmentOpt = agent.hasIRSAAGENT
    auditData.set("isEnrolledSAAgent", saEnrolmentOpt.isDefined)

    for {
      e            <- payeEnrolmentOpt
      payeAgentRef <- e.identifiers.find(_.key == "IRAgentReference")
    } auditData.set("payeAgentRef", payeAgentRef.value)

    for {
      e          <- saEnrolmentOpt
      saAgentRef <- e.identifiers.find(_.key == "IRAgentReference")
    } auditData.set("saAgentRef", saAgentRef.value)

    assuranceCheckInput.foreach { userInput =>
      userInput.passCesaAgentAssuranceCheck.foreach { assuranceCheck =>
        auditData.set("passCESAAgentAssuranceCheck", assuranceCheck)
      }
      userInput.userEnteredNino.foreach { nino =>
        auditData.set("userEnteredNino", nino)
      }
      userInput.userEnteredUtr.foreach { utr =>
        auditData.set("userEnteredUtr", utr)
      }
      userInput.userEnteredSaAgentRef.foreach { saAgentRef =>
        auditData.set("userEnteredSaAgentRef", saAgentRef)
      }
    }

    auditData.set("authProviderId", agent.authProviderId)
    auditData.set("authProviderType", agent.authProviderType)

    sendAgentAssuranceAuditEvent(auditData)
  }

  def sendAgentAssuranceAuditEvent(
    auditData: AuditData)(implicit hc: HeaderCarrier, request: Request[Any]): Future[Unit] =
    auditEvent(AgentAssurance, "agent-assurance", collectDetails(auditData.getDetails, agentAssuranceDetailsFields))

  private[audit] def collectDetails(data: Map[String, Any], fields: Seq[(String, Option[Any])]): Seq[(String, Any)] =
    fields.collect {
      case (f, _) if data.isDefinedAt(f) => (f, data(f))
      case (f, Some(d))                  => (f, d)
    }

  private[audit] def auditEvent(
    event: AgentSubscriptionFrontendEvent,
    transactionName: String,
    details: Seq[(String, Any)] = Seq.empty)(implicit hc: HeaderCarrier, request: Request[Any]): Future[Unit] =
    send(createEvent(event, transactionName, details: _*))

  private[audit] def createEvent(
    event: AgentSubscriptionFrontendEvent,
    transactionName: String,
    details: (String, Any)*)(implicit hc: HeaderCarrier, request: Request[Any]): DataEvent = {

    def toString(x: Any): String = x match {
      case t: TaxIdentifier => t.value
      case _                => x.toString
    }

    val detail = hc.toAuditDetails(details.map(pair => pair._1 -> toString(pair._2)): _*)
    val tags = hc.toAuditTags(transactionName, request.path)
    DataEvent(auditSource = "agent-subscription-frontend", auditType = event.toString, tags = tags, detail = detail)
  }

  private[audit] def send(events: DataEvent*)(implicit hc: HeaderCarrier): Future[Unit] =
    Future {
      events.foreach { event =>
        Try(auditConnector.sendEvent(event))
      }
    }

}
