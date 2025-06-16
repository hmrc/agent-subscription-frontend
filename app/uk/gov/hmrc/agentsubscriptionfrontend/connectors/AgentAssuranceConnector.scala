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
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentChecksResponse, AmlsDetails}
import uk.gov.hmrc.agentsubscriptionfrontend.util.HttpAPIMonitor
import uk.gov.hmrc.agentsubscriptionfrontend.util.HttpClientConverter.transformOptionResponse
import uk.gov.hmrc.agentsubscriptionfrontend.util.RequestSupport.hc
import uk.gov.hmrc.domain.{SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http.HttpErrorFunctions._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentAssuranceConnector @Inject() (httpClientV2: HttpClientV2, val metrics: Metrics, appConfig: AppConfig)(implicit val ec: ExecutionContext)
    extends HttpAPIMonitor {

  def hasAcceptableNumberOfClients(regime: String)(implicit rh: RequestHeader): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-hasAcceptableNumberOfClients-GET") {
      val urlString = s"${appConfig.agentAssuranceBaseUrl}/agent-assurance/acceptableNumberOfClients/service/$regime"
      httpClientV2
        .get(url"$urlString")
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case NO_CONTENT               => true
            case s if is2xx(s)            => false
            case UNAUTHORIZED | FORBIDDEN => false
            case s                        => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def getActiveCesaRelationship(uri: String)(implicit rh: RequestHeader): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
      val urlString = s"${appConfig.agentAssuranceBaseUrl}$uri"
      httpClientV2
        .get(url"$urlString")
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case s if is2xx(s)         => true
            case FORBIDDEN | NOT_FOUND => false
            case s                     => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def agentChecks(utr: String)(implicit rh: RequestHeader): Future[AgentChecksResponse] =
    monitor(s"ConsumedAPI-AgentAssurance-getAgentChecks-GET") {
      val urlString = s"${appConfig.agentAssuranceBaseUrl}/agent-assurance/restricted-collection-check/utr/$utr?nameRequired=false"
      httpClientV2
        .get(url"$urlString")
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case OK => response.json.as[AgentChecksResponse]
            case NOT_FOUND =>
              throw new IllegalStateException(
                s"unable to reach $urlString. AgentCheck for Manually assured or R2dw list might not have been configured"
              )
            case s =>
              throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def getAmlsData(utr: String)(implicit rh: RequestHeader): Future[Option[AmlsDetails]] =
    monitor(s"ConsumedAPI-AgentAssurance-getAmlsData-GET") {
      val urlString = s"${appConfig.agentAssuranceBaseUrl}/agent-assurance/amls/utr/$utr"
      transformOptionResponse[AmlsDetails](
        httpClientV2.get(url"$urlString").execute[HttpResponse]
      )
        .recover {
          case e: UpstreamErrorResponse if e.statusCode == 404 => None
          case e                                               => throw e
        }
    }

  private def cesaGetUrl(ninoOrUtr: String, valueOfNinoOrUtr: String, saAgentReference: SaAgentReference): String =
    s"/agent-assurance/activeCesaRelationship/$ninoOrUtr/$valueOfNinoOrUtr/saAgentReference/${saAgentReference.value}"

  def hasActiveCesaRelationship(ninoOrUtr: TaxIdentifier, taxIdName: String, saAgentReference: SaAgentReference)(implicit
    rh: RequestHeader
  ): Future[Boolean] =
    getActiveCesaRelationship(cesaGetUrl(taxIdName, ninoOrUtr.value, saAgentReference))

  def hasAcceptableNumberOfPayeClients(implicit rh: RequestHeader): Future[Boolean] =
    hasAcceptableNumberOfClients("IR-PAYE")

  def hasAcceptableNumberOfSAClients(implicit rh: RequestHeader): Future[Boolean] =
    hasAcceptableNumberOfClients("IR-SA")

  def hasAcceptableNumberOfVatDecOrgClients(implicit rh: RequestHeader): Future[Boolean] =
    hasAcceptableNumberOfClients("HMCE-VATDEC-ORG")

  def hasAcceptableNumberOfIRCTClients(implicit rh: RequestHeader): Future[Boolean] =
    hasAcceptableNumberOfClients("IR-CT")
}
