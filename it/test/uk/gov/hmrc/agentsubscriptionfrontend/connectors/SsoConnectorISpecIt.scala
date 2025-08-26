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

import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.SsoStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpecIt
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext.Implicits.global

class SsoConnectorISpecIt extends BaseISpecIt {

  private lazy val connector =
    new SsoConnector(app.injector.instanceOf[HttpClientV2], app.injector.instanceOf[Metrics], app.injector.instanceOf[AppConfig])
  private implicit val hc = HeaderCarrier()

  "SsoConnector" should {
    "return valid domains" in {
      SsoStub.givenAllowlistedDomainsExist
      val result = await(connector.getAllowlistedDomains())
      result shouldBe Set("online-qa.ibt.hmrc.gov.uk", "localhost", "ibt.hmrc.gov.uk", "127.0.0.1", "www.tax.service.gov.uk")

    }

    "return an empty set when the service throws an error" in {
      SsoStub.givenAllowlistedDomainsError
      val result = await(connector.getAllowlistedDomains())
      result shouldBe Set.empty
    }

  }
}
