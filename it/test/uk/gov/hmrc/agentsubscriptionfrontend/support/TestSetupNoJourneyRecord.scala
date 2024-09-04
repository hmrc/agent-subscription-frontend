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

package uk.gov.hmrc.agentsubscriptionfrontend.support

import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, BusinessDetails, SubscriptionJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub.givenAgentIsNotManuallyAssured
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.{givenNoSubscriptionJourneyRecordExists, givenSubscriptionJourneyRecordExists}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.SsoStub

import java.time.LocalDate

trait TestSetupNoJourneyRecord {
  SsoStub.givenAllowlistedDomainsExist
  givenNoSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"))
}

trait TestSetupWithCompleteJourneyRecord {
  givenSubscriptionJourneyRecordExists(
    AuthProviderId("12345-credId"),
    SubscriptionJourneyRecord(
      authProviderId = AuthProviderId("12345-credId"),
      continueId = None,
      businessDetails = BusinessDetails(
        SoleTrader,
        "8699323569",
        "GU95 5MT",
        Some(
          Registration(
            Some("tax name"),
            isSubscribedToAgentServices = true,
            isSubscribedToETMP = true,
            BusinessAddress("line 1", Some("line 2"), Some("line 3"), Some("line 4"), Some("POST"), "GB"),
            Some("abc@xyz.com"),
            Some("01273111111"),
            Some("safeId")
          )
        )
      ),
      amlsData = Some(
        AmlsData(
          amlsRegistered = true,
          Some(false),
          Some(AmlsDetails("supervisory", membershipNumber = Some("memNumber"), appliedOn = None, membershipExpiresOn = Some(LocalDate.now())))
        )
      ),
      cleanCredsAuthProviderId = Some(AuthProviderId("1234-creds")),
      contactEmailData = Some(ContactEmailData(useBusinessEmail = true, Some("abc@xyz.com"))),
      contactTradingNameData = Some(ContactTradingNameData(hasTradingName = false, None)),
      contactTradingAddressData = Some(
        ContactTradingAddressData(
          useBusinessAddress = true,
          Some(BusinessAddress("line 1", Some("line 2"), Some("line 3"), Some("line 4"), Some("POST"), "GB"))
        )
      )
    )
  )

  givenAgentIsNotManuallyAssured("8699323569")
}
