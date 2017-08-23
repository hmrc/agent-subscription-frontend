/*
 * Copyright 2017 HM Revenue & Customs
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

import java.net.URL
import javax.inject.{Inject, Named}

import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

class AuthenticatorConnector @Inject()(@Named("government-gateway-authentication-baseUrl") baseUrl: URL, http: HttpGet with HttpPost) {

  def refreshEnrolments(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POSTEmpty(s"$baseUrl/government-gateway-authentication/refresh-profile").map { httpResponse =>
      if(httpResponse.status != 204) throw new RuntimeException(s"Receive unexpected response from authenticator proxy, with status ${httpResponse.status}")
    }
  }
}