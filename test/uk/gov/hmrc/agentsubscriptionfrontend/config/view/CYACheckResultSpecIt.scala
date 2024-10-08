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

package uk.gov.hmrc.agentsubscriptionfrontend.config.view

import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, BusinessDetails, SubscriptionJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.support.UnitSpec

class CYACheckResultSpecIt extends UnitSpec {

  val id = AuthProviderId("12345-credId")
  val validUtr = "2000000000"
  val validPostcode = "AA1 1AA"
  val registrationName = "My Agency"
  val tradingName = "My Trading Name"
  val businessAddress =
    BusinessAddress("AddressLine1 A", Some("AddressLine2 A"), Some("AddressLine3 A"), Some("AddressLine4 A"), Some("AA11AA"), "GB")
  val telephoneNumber = "01273111111"

  val amlsData: AmlsData = AmlsData.registeredUserNoDataEntered

  val testRegistration: Registration =
    Registration(
      Some(registrationName),
      isSubscribedToAgentServices = false,
      isSubscribedToETMP = false,
      businessAddress,
      Some("test@gmail.com"),
      Some(telephoneNumber),
      Some("safeId")
    )

  "CYACheckResult" should {

    "PassWithMaybeAmls when SubscriptionJourneyRecord is complete including Amls" in {

      val sjr = SubscriptionJourneyRecord(
        authProviderId = id,
        businessDetails = BusinessDetails(SoleTrader, validUtr, validPostcode, registration = Some(testRegistration)),
        amlsData = Some(amlsData),
        contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
        contactTradingNameData = Some(ContactTradingNameData(true, Some("My Trading Name"))),
        contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress))),
        contactTelephoneData = Some(ContactTelephoneData(true, Some(telephoneNumber)))
      )

      CYACheckResult.check(sjr) shouldBe PassWithMaybeAmls(
        registrationName,
        businessAddress,
        Some(amlsData),
        "email@email.com",
        Some("My Trading Name"),
        businessAddress,
        telephoneNumber
      )
    }

    "PassWithMaybeAmls when SubscriptionJourneyRecord is complete without Amls" in {

      val sjr = SubscriptionJourneyRecord(
        authProviderId = id,
        businessDetails = BusinessDetails(SoleTrader, validUtr, validPostcode, registration = Some(testRegistration)),
        amlsData = None,
        contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
        contactTradingNameData = Some(ContactTradingNameData(true, Some("My Trading Name"))),
        contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress))),
        contactTelephoneData = Some(ContactTelephoneData(true, Some(telephoneNumber)))
      )

      CYACheckResult.check(sjr) shouldBe PassWithMaybeAmls(
        registrationName,
        businessAddress,
        None,
        "email@email.com",
        Some("My Trading Name"),
        businessAddress,
        telephoneNumber
      )
    }

    "FailedRegistration when SubscriptionJourneyRecord is missing Registration" in {

      val sjr = SubscriptionJourneyRecord(
        authProviderId = id,
        businessDetails = BusinessDetails(SoleTrader, validUtr, validPostcode, registration = None),
        amlsData = None,
        contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
        contactTradingNameData = Some(ContactTradingNameData(true, Some("My Trading Name"))),
        contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress))),
        contactTelephoneData = Some(ContactTelephoneData(true, Some(telephoneNumber)))
      )

      CYACheckResult.check(sjr) shouldBe FailedRegistration
    }

    "FailedContactEmail when SubscriptionJourneyRecord is complete except no contact email data" in {

      val sjr = SubscriptionJourneyRecord(
        authProviderId = id,
        businessDetails = BusinessDetails(SoleTrader, validUtr, validPostcode, registration = Some(testRegistration)),
        amlsData = None,
        contactEmailData = None,
        contactTradingNameData = Some(ContactTradingNameData(true, Some("My Trading Name"))),
        contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress))),
        contactTelephoneData = Some(ContactTelephoneData(true, Some(telephoneNumber)))
      )

      CYACheckResult.check(sjr) shouldBe FailedContactEmail
    }

    "FailedContactEmail when SubscriptionJourneyRecord is complete except no contact email address defined" in {

      val sjr = SubscriptionJourneyRecord(
        authProviderId = id,
        businessDetails = BusinessDetails(SoleTrader, validUtr, validPostcode, registration = Some(testRegistration)),
        amlsData = None,
        contactEmailData = Some(ContactEmailData(true, None)),
        contactTradingNameData = Some(ContactTradingNameData(true, Some("My Trading Name"))),
        contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress))),
        contactTelephoneData = Some(ContactTelephoneData(true, Some(telephoneNumber)))
      )

      CYACheckResult.check(sjr) shouldBe FailedContactEmail
    }

    "FailedContactTradingName when SubscriptionJourneyRecord is complete except no contact trading name is defined" in {

      val sjr = SubscriptionJourneyRecord(
        authProviderId = id,
        businessDetails = BusinessDetails(SoleTrader, validUtr, validPostcode, registration = Some(testRegistration)),
        amlsData = None,
        contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
        contactTradingNameData = None,
        contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress))),
        contactTelephoneData = Some(ContactTelephoneData(true, Some(telephoneNumber)))
      )

      CYACheckResult.check(sjr) shouldBe FailedContactTradingName
    }

    "FailedContactTradingName when SubscriptionJourneyRecord is complete except contact trading name check is false but no trading name defined" in {

      val sjr = SubscriptionJourneyRecord(
        authProviderId = id,
        businessDetails = BusinessDetails(SoleTrader, validUtr, validPostcode, registration = Some(testRegistration)),
        amlsData = None,
        contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
        contactTradingNameData = Some(ContactTradingNameData(false, None)),
        contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress))),
        contactTelephoneData = Some(ContactTelephoneData(true, Some(telephoneNumber)))
      )

      CYACheckResult.check(sjr) shouldBe FailedContactTradingName
    }

    "FailedContactTradingAddress when SubscriptionJourneyRecord is complete except no contact trading address data is defined" in {

      val sjr = SubscriptionJourneyRecord(
        authProviderId = id,
        businessDetails = BusinessDetails(SoleTrader, validUtr, validPostcode, registration = Some(testRegistration)),
        amlsData = None,
        contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
        contactTradingNameData = Some(ContactTradingNameData(true, Some("My Trading Name"))),
        contactTradingAddressData = None,
        contactTelephoneData = Some(ContactTelephoneData(true, Some(telephoneNumber)))
      )

      CYACheckResult.check(sjr) shouldBe FailedContactTradingAddress
    }

    "FailedContactTradingAddress when SubscriptionJourneyRecord is complete except no trading address defined" in {

      val sjr = SubscriptionJourneyRecord(
        authProviderId = id,
        businessDetails = BusinessDetails(SoleTrader, validUtr, validPostcode, registration = Some(testRegistration)),
        amlsData = Some(amlsData),
        contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
        contactTradingNameData = Some(ContactTradingNameData(true, Some("My Trading Name"))),
        contactTradingAddressData = Some(ContactTradingAddressData(true, None)),
        contactTelephoneData = Some(ContactTelephoneData(true, Some(telephoneNumber)))
      )

      CYACheckResult.check(sjr) shouldBe FailedContactTradingAddress
    }
  }

  "FailedContactTelephoneNumber when SubscriptionJourneyRecord is complete except no contact telephone data is defined" in {

    val sjr = SubscriptionJourneyRecord(
      authProviderId = id,
      businessDetails = BusinessDetails(SoleTrader, validUtr, validPostcode, registration = Some(testRegistration)),
      amlsData = None,
      contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
      contactTradingNameData = Some(ContactTradingNameData(true, Some("My Trading Name"))),
      contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress))),
      contactTelephoneData = None
    )

    CYACheckResult.check(sjr) shouldBe FailedContactTelephone
  }

  "FailedContactTelephoneNumber when SubscriptionJourneyRecord is complete except no contact telephone is defined" in {

    val sjr = SubscriptionJourneyRecord(
      authProviderId = id,
      businessDetails = BusinessDetails(SoleTrader, validUtr, validPostcode, registration = Some(testRegistration)),
      amlsData = Some(amlsData),
      contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
      contactTradingNameData = Some(ContactTradingNameData(true, Some("My Trading Name"))),
      contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress))),
      contactTelephoneData = Some(ContactTelephoneData(true, None))
    )

    CYACheckResult.check(sjr) shouldBe FailedContactTelephone
  }
}
