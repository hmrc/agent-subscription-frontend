/*
 * Copyright 2020 HM Revenue & Customs
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

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.libs.json.JsObject
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads.Implicits._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SsoConnector @Inject()(http: HttpClient, metrics: Metrics, appConfig: AppConfig)(
  implicit val ec: ExecutionContext)
    extends HttpAPIMonitor with Logging {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getWhitelistedDomains()(implicit hc: HeaderCarrier): Future[Set[String]] =
    monitor(s"ConsumedAPI-SSO-getExternalDomains-GET") {
      val url = s"${appConfig.ssoBaseUrl}/sso/domains"
      http
        .GET[JsObject](url)
        .map(jsObj => {
          (jsObj \ "externalDomains").as[Set[String]] ++ (jsObj \ "internalDomains").as[Set[String]]
        })
        .recover {
          case e =>
            logger.error(s"retrieval of whitelisted domains failed: $e")
            Set.empty[String]
        }
    }
}
