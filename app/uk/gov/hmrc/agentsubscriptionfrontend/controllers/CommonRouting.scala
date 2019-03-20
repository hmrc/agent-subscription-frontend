/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent.hasNonEmptyEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, Llp, Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.MappingEligibility.IsEligible
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, MappingEligibility}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait CommonRouting {

  val appConfig: AppConfig

  private[controllers] def handleAutoMapping(eligibleForMapping: Option[Boolean])(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] =
    MappingEligibility.apply(eligibleForMapping) match {
      case IsEligible if appConfig.autoMapAgentEnrolments =>
        toFuture(Redirect(routes.SubscriptionController.showLinkClients()))
      case _ => toFuture(Redirect(routes.SubscriptionController.showCheckAnswers()))
    }

  private[controllers] def withCleanCreds(agent: Agent)(f: => Future[Result]): Future[Result] =
    agent match {
      case hasNonEmptyEnrolments(_) =>
        toFuture(Redirect(routes.BusinessIdentificationController.showCreateNewAccount()))
      case _ => f
    }

  protected def redirectToNextPage(agentSession: AgentSession): Call =
    agentSession match {
      case _ if agentSession.businessType.isEmpty => routes.BusinessIdentificationController.showBusinessTypeForm()
      case _ if agentSession.utr.isEmpty          => routes.BusinessIdentificationController.showUtrForm()
      case _ if agentSession.postcode.isEmpty     => routes.BusinessIdentificationController.showPostcodeForm()
      case _ if agentSession.postcode.isDefined   => redirectAfterPostcode(agentSession)
    }

  private def redirectAfterPostcode(agentSession: AgentSession): Call =
    agentSession.businessType match {
      case Some(SoleTrader | Partnership) => continueFromNationalInsurancePage(agentSession)
      case Some(LimitedCompany | Llp)     => continueFromCompanyRegistrationPage(agentSession)
    }

  private def continueFromNationalInsurancePage(agentSession: AgentSession) =
    agentSession match {
      case _ if agentSession.nino.isEmpty        => routes.BusinessIdentificationController.showNationalInsuranceNumberForm()
      case _ if agentSession.dateOfBirth.isEmpty => routes.DateOfBirthController.showDateOfBirthForm()
      case _ if agentSession.companyRegistrationNumber.isEmpty =>
        routes.RegisteredForVatController.showRegisteredForVatForm()
    }

  private def continueFromCompanyRegistrationPage(agentSession: AgentSession) =
    agentSession match {
      case _ if agentSession.companyRegistrationNumber.isEmpty =>
        routes.CompanyRegistrationController.showCompanyRegNumberForm()
      case _ if agentSession.companyRegistrationNumber.isDefined =>
        routes.RegisteredForVatController.showRegisteredForVatForm()
    }
}
