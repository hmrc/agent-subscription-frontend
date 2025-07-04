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
import play.api.libs.json.JsObject
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.util.HttpAPIMonitor
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SsoConnector @Inject() (http: HttpClientV2, val metrics: Metrics, appConfig: AppConfig)(implicit val ec: ExecutionContext)
    extends HttpAPIMonitor with Logging {

  def getAllowlistedDomains()(implicit hc: HeaderCarrier): Future[Set[String]] =
    monitor(s"ConsumedAPI-SSO-getExternalDomains-GET") {
      val url = s"${appConfig.ssoBaseUrl}/sso/domains"
      http
        .get(url"$url")
        .execute[JsObject]
        .map(jsObj => (jsObj \ "externalDomains").as[Set[String]] ++ (jsObj \ "internalDomains").as[Set[String]])
        .recover { case e =>
          logger.error(s"retrieval of allowlisted domains failed: $e")
          Set.empty[String]
        }
    }
}
