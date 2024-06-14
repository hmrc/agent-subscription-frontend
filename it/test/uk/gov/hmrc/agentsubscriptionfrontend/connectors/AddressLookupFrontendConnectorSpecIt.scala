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
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AddressLookupFrontendAddress, Country}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AddressLookupFrontendStubs.givenAddressLookupReturnsAddress
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpecIt, MetricTestSupport}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class AddressLookupFrontendConnectorSpecIt extends BaseISpecIt with MetricTestSupport {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "getAddressDetails" should {
    "convert the JSON returned by address-lookup-frontend into an object" in {
      withMetricsTimerUpdate("ConsumedAPI-Address-Lookup-Frontend-getAddressDetails-GET") {
        val addressId = "id"
        val addressLine1 = "10 Other Place"
        val addressLine2 = "Some District"
        val addressLine3 = "Line 3"
        val town = "Our town"
        val postcode = "AA1 1AA"
        givenAddressLookupReturnsAddress(addressId, addressLine1, addressLine2, addressLine3, town, postcode)
        val connector = app.injector.instanceOf[AddressLookupFrontendConnector]
        val address = await(connector.getAddressDetails(addressId))
        address shouldBe AddressLookupFrontendAddress(
          lines = Seq(addressLine1, addressLine2, addressLine3, town),
          postcode = Some(postcode),
          country = Country("GB", Some("United Kingdom"))
        )
      }
    }
  }

}
