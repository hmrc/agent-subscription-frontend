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
import org.apache.commons.lang3.RandomStringUtils
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, Llp, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney._
import uk.gov.hmrc.domain.AgentCode

import java.time.LocalDate

object TestData {

  val validBusinessTypes: Seq[BusinessType] =
    Seq(BusinessType.SoleTrader, BusinessType.LimitedCompany, BusinessType.Partnership, BusinessType.Llp)

  val validUtr = "2000000000"
  val validPostcode = "AA1 1AA"
  val invalidPostcode = "11AAAA"
  val denylistedPostcode = "AB10 1ZT"
  val phoneNumber = "01273111111"
  val utr = "2000000000"
  val testPostcode = "AA1 1AA"
  val registrationName = "My Agency"
  val tradingName = "My Trading Name"
  val emailTooLong: String = RandomStringUtils.randomAlphanumeric(250).concat("@a.a")
  val businessAddress: BusinessAddress =
    BusinessAddress("AddressLine1 A", Some("AddressLine2 A"), Some("AddressLine3 A"), Some("AddressLine4 A"), Some("AA11AA"), "GB")

  val tradingAddress: BusinessAddress =
    BusinessAddress("TradingAddress1 A", Some("TradingAddress2 A"), Some("TradingAddress3 A"), Some("TradingAddress4 A"), Some("TT11TT"), "GB")

  val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"

  val agentSession: AgentSession =
    AgentSession(businessType = Some(SoleTrader), utr = Some(validUtr), postcode = Some("bn13 1hn"), nino = Some("AE123456C"))

  val agentSessionForLimitedCompany: AgentSession = agentSession.copy(businessType = Some(LimitedCompany))

  val agentSessionForLimitedPartnership: AgentSession = agentSession.copy(businessType = Some(Llp))

  val testRegistration: Registration = Registration(
    Some(registrationName),
    isSubscribedToAgentServices = false,
    isSubscribedToETMP = false,
    businessAddress,
    Some("test@gmail.com"),
    Some(phoneNumber),
    Some("safeId")
  )

  val id: AuthProviderId = AuthProviderId("12345-credId")

  val record: SubscriptionJourneyRecord = TestData.minimalSubscriptionJourneyRecord(id)

  def minimalSubscriptionJourneyRecord(authProviderId: AuthProviderId): SubscriptionJourneyRecord =
    SubscriptionJourneyRecord(authProviderId, businessDetails = BusinessDetails(SoleTrader, validUtr, validPostcode))

  val couldBePartiallySubscribedJourneyRecord: SubscriptionJourneyRecord =
    SubscriptionJourneyRecord(
      id,
      businessDetails = BusinessDetails(
        LimitedCompany,
        validUtr,
        validPostcode,
        registration = Some(
          Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("test@gmail.com"),
            Some(phoneNumber),
            Some("safeId")
          )
        ),
        nino = None,
        companyRegistrationNumber = Some(CompanyRegistrationNumber("01234567")),
        dateOfBirth = None,
        registeredForVat = Some(false),
        vatDetails = None
      ),
      continueId = Some("/continue"),
      amlsData = None,
      userMappings = List.empty[UserMapping],
      cleanCredsAuthProviderId = Some(id),
      contactEmailData = None,
      contactTradingAddressData = None,
      contactTradingNameData = None,
      contactTelephoneData = None
    )

  def minimalSubscriptionJourneyRecordWithAmls(authProviderId: AuthProviderId): SubscriptionJourneyRecord =
    SubscriptionJourneyRecord(
      authProviderId,
      businessDetails = BusinessDetails(SoleTrader, validUtr, validPostcode),
      amlsData = Some(AmlsData.registeredUserNoDataEntered)
    )

  val completeJourneyRecordNoMappings: SubscriptionJourneyRecord = SubscriptionJourneyRecord(
    authProviderId = AuthProviderId("12345-credId"),
    continueId = None,
    businessDetails = BusinessDetails(
      SoleTrader,
      validUtr,
      validPostcode,
      registration = Some(
        Registration(
          Some(registrationName),
          isSubscribedToAgentServices = true,
          isSubscribedToETMP = true,
          businessAddress,
          Some("test@gmail.com"),
          Some(phoneNumber),
          Some("safeId")
        )
      )
    ),
    amlsData = Some(
      AmlsData(
        amlsRegistered = true,
        Some(false),
        Some(
          AmlsDetails("supervisory", membershipNumber = Some("123456789"), appliedOn = None, membershipExpiresOn = Some(LocalDate.now().plusDays(10)))
        )
      )
    ),
    cleanCredsAuthProviderId = Some(id),
    contactEmailData = Some(ContactEmailData(useBusinessEmail = false, Some("email@email.com"))),
    contactTradingNameData = Some(ContactTradingNameData(hasTradingName = true, Some(tradingName))),
    contactTradingAddressData = Some(ContactTradingAddressData(useBusinessAddress = true, Some(businessAddress))),
    contactTelephoneData = Some(ContactTelephoneData(useBusinessTelephone = true, Some(phoneNumber))),
    verifiedEmails = VerifiedEmails(Set("email@email.com"))
  )
  val completeJourneyRecordWithMappingsNoVerifiedEmails: SubscriptionJourneyRecord = completeJourneyRecordNoMappings
    .copy(
      verifiedEmails = VerifiedEmails()
    )

  val completeJourneyRecordWithMappings: SubscriptionJourneyRecord = completeJourneyRecordNoMappings
    .copy(
      userMappings = List(
        UserMapping(AuthProviderId("map-1"), Some(AgentCode("ACODE")), List.empty, 20, "1234"),
        UserMapping(AuthProviderId("map-2"), Some(AgentCode("BCODE")), List.empty, 20, "5678")
      )
    )

  def completeJourneyRecordWithMappingsAndNewTradingDetails(
    tradingName: Option[String],
    tradingAddress: Option[BusinessAddress]
  ): SubscriptionJourneyRecord =
    completeJourneyRecordWithMappings
      .copy(
        contactTradingNameData = Some(ContactTradingNameData(hasTradingName = true, tradingName)),
        contactTradingAddressData = Some(ContactTradingAddressData(useBusinessAddress = true, Some(businessAddress))),
        contactTelephoneData = Some(ContactTelephoneData(useBusinessTelephone = true, Some(phoneNumber)))
      )

  def completeJourneyRecordWithUpdatedBusinessName(newBusinessName: String): SubscriptionJourneyRecord =
    completeJourneyRecordNoMappings.copy(businessDetails =
      BusinessDetails(
        SoleTrader,
        validUtr,
        validPostcode,
        Some(
          Registration(
            Some(newBusinessName),
            isSubscribedToAgentServices = true,
            isSubscribedToETMP = true,
            businessAddress,
            Some("test@gmail.com"),
            Some(phoneNumber),
            Some("safeId")
          )
        )
      )
    )

  def completeJourneyRecordWithUpdatedBusinessEmail(newBusinessEmail: String): SubscriptionJourneyRecord =
    completeJourneyRecordNoMappings.copy(businessDetails =
      BusinessDetails(
        SoleTrader,
        validUtr,
        validPostcode,
        Some(
          Registration(
            Some(registrationName),
            isSubscribedToAgentServices = true,
            isSubscribedToETMP = true,
            businessAddress,
            Some(newBusinessEmail),
            Some(phoneNumber),
            Some("safeId")
          )
        )
      )
    )

}
