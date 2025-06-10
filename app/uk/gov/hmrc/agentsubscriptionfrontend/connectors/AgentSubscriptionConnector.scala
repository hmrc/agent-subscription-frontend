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

import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.RequestHeader
import play.utils.UriEncoding
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Vrn}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.util.HttpAPIMonitor
import uk.gov.hmrc.agentsubscriptionfrontend.util.HttpClientConverter._
import uk.gov.hmrc.agentsubscriptionfrontend.util.RequestSupport.hc
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpErrorFunctions._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentSubscriptionConnector @Inject() (
  http: HttpClientV2,
  val metrics: Metrics,
  appConfig: AppConfig
)(implicit val ec: ExecutionContext)
    extends HttpAPIMonitor with Logging {

  def getJourneyById(internalId: AuthProviderId)(implicit rh: RequestHeader): Future[Option[SubscriptionJourneyRecord]] =
    monitor(s"ConsumedAPI-Agent-Subscription-getJourneyByPrimaryId-GET") {
      http
        .get(url"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/subscription/journey/id/${encodePathSegment(internalId.id)}")
        .execute[HttpResponse]
        .map(response =>
          response.status match {
            case 200           => Some(Json.parse(response.body).as[SubscriptionJourneyRecord])
            case s if is2xx(s) => None
            case s             => throw UpstreamErrorResponse(response.body, s)
          }
        )
    }

  def getJourneyByContinueId(continueId: ContinueId)(implicit rh: RequestHeader): Future[Option[SubscriptionJourneyRecord]] =
    monitor(s"ConsumedAPI-Agent-Subscription-getJourneyByContinueId-GET") {
      transformOptionResponse[SubscriptionJourneyRecord](
        http
          .get(url"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/subscription/journey/continueId/${encodePathSegment(continueId.value)}")
          .execute[HttpResponse]
      )
    }

  def getJourneyByUtr(utr: String)(implicit rh: RequestHeader): Future[Option[SubscriptionJourneyRecord]] =
    monitor(s"ConsumedAPI-Agent-Subscription-getJourneyByUtr-GET") {
      transformOptionResponse[SubscriptionJourneyRecord](
        http
          .get(url"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/subscription/journey/utr/${encodePathSegment(utr)}")
          .execute[HttpResponse]
      )
    }

  def createOrUpdateJourney(journeyRecord: SubscriptionJourneyRecord)(implicit rh: RequestHeader): Future[Int] =
    monitor("ConsumedAPI-Agent-Subscription-createOrUpdate-POST") {
      http
        .post(
          url"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/subscription/journey/primaryId/${encodePathSegment(journeyRecord.authProviderId.id)}"
        )
        .withBody(Json.toJson(journeyRecord))
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case s if is2xx(s) => s
            case s =>
              logger.error(s"creating subscription journey record failed for reason: ${response.body}")
              throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def getRegistration(utr: String, postcode: String)(implicit rh: RequestHeader): Future[Option[Registration]] =
    monitor(s"ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
      http
        .get(url"${getRegistrationUrlFor(utr, postcode)}")
        .execute[Option[Registration]]
    }

  def matchCorporationTaxUtrWithCrn(utr: String, crn: CompanyRegistrationNumber)(implicit rh: RequestHeader): Future[Boolean] =
    monitor(s"ConsumedAPI-Agent-Subscription-matchCorporationTaxUtrWithCrn-GET") {
      http
        .get(
          url"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/corporation-tax-utr/${encodePathSegment(utr)}/crn/${encodePathSegment(crn.value)}"
        )
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case OK            => true
            case s if is2xx(s) => false
            case NOT_FOUND     => false
            case s             => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def matchVatKnownFacts(vrn: Vrn, vatRegistrationDate: LocalDate)(implicit rh: RequestHeader): Future[Boolean] =
    monitor(s"ConsumedAPI-Agent-Subscription-matchVatKnownFacts-GET") {
      http
        .get(
          url"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/vat-known-facts/vrn/${encodePathSegment(vrn.value)}/dateOfRegistration/${encodePathSegment(vatRegistrationDate.toString)}"
        )
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case OK            => true
            case s if is2xx(s) => false
            case NOT_FOUND     => false
            case s             => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def subscribeAgencyToMtd(subscriptionRequest: SubscriptionRequest)(implicit rh: RequestHeader): Future[Arn] =
    monitor(s"ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST") {
      transformResponse[JsValue](
        http
          .post(url"$subscriptionUrl")
          .withBody(Json.toJson(subscriptionRequest))
          .execute[HttpResponse]
      )
        .map(js => (js \ "arn").as[Arn])
    }

  def completePartialSubscription(details: CompletePartialSubscriptionBody)(implicit rh: RequestHeader): Future[Arn] =
    monitor(s"ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
      transformResponse[JsValue](
        http.put(url"$subscriptionUrl").withBody(Json.toJson(details)).execute[HttpResponse]
      )
        .map(js => (js \ "arn").as[Arn])
    }

  def getDesignatoryDetails(nino: Nino)(implicit rh: RequestHeader): Future[DesignatoryDetails] =
    monitor(s"ConsumedAPI-Agent-Subscription-getDesignatoryDetails-GET") {
      transformResponse[DesignatoryDetails](
        http
          .get(url"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/citizen-details/${nino.value}/designatory-details")
          .execute[HttpResponse]
      )
        .recover {
          case ex: UpstreamErrorResponse if ex.statusCode == NOT_FOUND => DesignatoryDetails()
        }
    }

  def companiesHouseKnownFactCheck(crn: CompanyRegistrationNumber, surname: String)(implicit rh: RequestHeader): Future[Int] =
    monitor(s"ConsumedAPI-Agent-Subscription-getCompanyOfficers-GET") {
      val encodedCrn = UriEncoding.encodePathSegment(crn.value, "UTF-8")
      val encodedName = UriEncoding.encodePathSegment(surname.toUpperCase, "UTF-8")
      http
        .get(url"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/companies-house-api-proxy/company/$encodedCrn/officers/$encodedName")
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case s if Seq(OK, NOT_FOUND, CONFLICT).contains(s) => s
            case s                                             => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def companiesHouseStatusCheck(crn: CompanyRegistrationNumber)(implicit rh: RequestHeader): Future[Int] =
    monitor(s"ConsumedAPI-Agent-Subscription-getCompanyStatus-GET") {
      val encodedCrn = UriEncoding.encodePathSegment(crn.value, "UTF-8")
      http
        .get(url"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/companies-house-api-proxy/company/$encodedCrn/status")
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case s if Seq(OK, NOT_FOUND, CONFLICT).contains(s) => s
            case s                                             => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def getAmlsSubscriptionRecord(amlsRegistrationNumber: String)(implicit rh: RequestHeader): Future[Option[AmlsSubscriptionRecord]] =
    monitor(s"ConsumedAPI-Agent-Subscription-getAmlsSubscription-GET") {
      http
        .get(url"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/amls-subscription/$amlsRegistrationNumber")
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case OK        => response.json.asOpt[AmlsSubscriptionRecord]
            case NOT_FOUND => None
            case status    => throw UpstreamErrorResponse("getAmlsSubscriptionRecord", status)
          }
        }
    }

  private val subscriptionUrl = s"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/subscription"

  private def getRegistrationUrlFor(utr: String, postcode: String) =
    s"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/registration/${encodePathSegment(utr)}/postcode/${encodePathSegment(postcode)}"
}
