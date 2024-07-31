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

import play.api.http.HeaderNames.LOCATION
import play.api.i18n.Lang
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Call
import uk.gov.hmrc.agentsubscriptionfrontend.config.{AddressLookupConfig, AppConfig}
import uk.gov.hmrc.agentsubscriptionfrontend.models.AddressLookupFrontendAddress
import uk.gov.hmrc.agentsubscriptionfrontend.util.HttpAPIMonitor
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

@Singleton
class AddressLookupFrontendConnector @Inject() (
  http: HttpClient,
  val metrics: Metrics,
  addressLookupConfig: AddressLookupConfig,
  appConfig: AppConfig
)(implicit val ec: ExecutionContext)
    extends HttpAPIMonitor {

  def initJourney(call: Call)(implicit hc: HeaderCarrier, ec: ExecutionContext, lang: Lang): Future[String] =
    monitor(s"ConsumedAPI-Address-Lookup-Frontend-initJourney-POST") {

      val addressConfig = Json.toJson(addressLookupConfig.config(s"${call.url}"))

      http.POST[JsValue, HttpResponse](initJourneyUrl, addressConfig) map { resp =>
        resp.header(LOCATION).getOrElse {
          throw new ALFLocationHeaderNotSetException
        }
      }
    }

  def getAddressDetails(id: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AddressLookupFrontendAddress] = {
    import AddressLookupFrontendAddress._

    monitor(s"ConsumedAPI-Address-Lookup-Frontend-getAddressDetails-GET") {
      http.GET[JsObject](confirmJourneyUrl(id)).map(json => (json \ "address").as[AddressLookupFrontendAddress])
    }
  }

  private def confirmJourneyUrl(id: String) =
    s"${appConfig.addressLookupFrontendBaseUrl}/api/confirmed?id=$id"

  private def initJourneyUrl: String =
    s"${appConfig.addressLookupFrontendBaseUrl}/api/v2/init"
}

class ALFLocationHeaderNotSetException extends NoStackTrace
