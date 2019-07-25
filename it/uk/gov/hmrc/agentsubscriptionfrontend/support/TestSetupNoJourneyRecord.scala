package uk.gov.hmrc.agentsubscriptionfrontend.support

import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.{givenNoSubscriptionJourneyRecordExists, givenSubscriptionJourneyRecordExists}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub.givenAgentIsNotManuallyAssured
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.completeJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.utr

trait TestSetupNoJourneyRecord {
  givenNoSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"))
}

