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

package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import play.api.http.Status._
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.AmlsDetails
import uk.gov.hmrc.agentsubscriptionfrontend.util.HttpAPIMonitor
import uk.gov.hmrc.agentsubscriptionfrontend.util.HttpClientConverter.transformOptionResponse
import uk.gov.hmrc.domain.{SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http.HttpErrorFunctions._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentAssuranceConnector @Inject() (http: HttpClient, val metrics: Metrics, appConfig: AppConfig)(implicit val ec: ExecutionContext)
    extends HttpAPIMonitor {

  def hasAcceptableNumberOfClients(regime: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-hasAcceptableNumberOfClients-GET") {
      http
        .GET[HttpResponse](s"${appConfig.agentAssuranceBaseUrl}/agent-assurance/acceptableNumberOfClients/service/$regime")
        .map { response =>
          response.status match {
            case NO_CONTENT               => true
            case s if is2xx(s)            => false
            case UNAUTHORIZED | FORBIDDEN => false
            case s                        => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def getActiveCesaRelationship(url: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
      http
        .GET[HttpResponse](s"${appConfig.agentAssuranceBaseUrl}$url")
        .map { response =>
          response.status match {
            case s if is2xx(s)         => true
            case FORBIDDEN | NOT_FOUND => false
            case s                     => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def isR2DWAgent(utr: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-getR2DWAgents-GET") {
      val endpoint = s"/agent-assurance/refusal-to-deal-with/utr/$utr"
      http
        .GET[HttpResponse](s"${appConfig.agentAssuranceBaseUrl}$endpoint")
        .map { response =>
          response.status match {
            case s if is2xx(s) => false
            case FORBIDDEN     => true
            case NOT_FOUND =>
              throw new IllegalStateException(
                s"unable to reach ${appConfig.agentAssuranceBaseUrl}$endpoint. R2dw list might not have been configured"
              )
            case s => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def isManuallyAssuredAgent(utr: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-getManuallyAssuredAgents-GET") {
      val endpoint = s"/agent-assurance/manually-assured/utr/$utr"
      http
        .GET[HttpResponse](s"${appConfig.agentAssuranceBaseUrl}$endpoint")
        .map { response =>
          response.status match {
            case s if is2xx(s) => true
            case FORBIDDEN     => false
            case NOT_FOUND =>
              throw new IllegalStateException(
                s"unable to reach ${appConfig.agentAssuranceBaseUrl}/$endpoint. Manually assured agents list might not have been configured"
              )
            case s => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def getAmlsData(utr: String)(implicit hc: HeaderCarrier): Future[Option[AmlsDetails]] =
    monitor(s"ConsumedAPI-AgentAssurance-getAmlsData-GET") {
      val endpoint = s"/agent-assurance/amls/utr/$utr"
      transformOptionResponse[AmlsDetails](http.GET[HttpResponse](s"${appConfig.agentAssuranceBaseUrl}$endpoint"))
        .recover {
          case e: UpstreamErrorResponse if e.statusCode == 404 => None
          case e                                               => throw e
        }
    }

  private def cesaGetUrl(ninoOrUtr: String, valueOfNinoOrUtr: String, saAgentReference: SaAgentReference): String =
    s"/agent-assurance/activeCesaRelationship/$ninoOrUtr/$valueOfNinoOrUtr/saAgentReference/${saAgentReference.value}"

  def hasActiveCesaRelationship(ninoOrUtr: TaxIdentifier, taxIdName: String, saAgentReference: SaAgentReference)(implicit
    hc: HeaderCarrier
  ): Future[Boolean] =
    getActiveCesaRelationship(cesaGetUrl(taxIdName, ninoOrUtr.value, saAgentReference))

  def hasAcceptableNumberOfPayeClients(implicit hc: HeaderCarrier): Future[Boolean] =
    hasAcceptableNumberOfClients("IR-PAYE")

  def hasAcceptableNumberOfSAClients(implicit hc: HeaderCarrier): Future[Boolean] =
    hasAcceptableNumberOfClients("IR-SA")

  def hasAcceptableNumberOfVatDecOrgClients(implicit hc: HeaderCarrier): Future[Boolean] =
    hasAcceptableNumberOfClients("HMCE-VATDEC-ORG")

  def hasAcceptableNumberOfIRCTClients(implicit hc: HeaderCarrier): Future[Boolean] =
    hasAcceptableNumberOfClients("IR-CT")
}
