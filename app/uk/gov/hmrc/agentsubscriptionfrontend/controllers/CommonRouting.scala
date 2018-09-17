/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.mvc._
import play.api.mvc.Results._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.MappingConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.{InitialDetails, MappingEligibility}
import uk.gov.hmrc.agentsubscriptionfrontend.models.MappingEligibility.{IsEligible, IsNotEligible, UnknownEligibility}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CommonRouting @Inject()(
  sessionStoreService: SessionStoreService,
  mappingConnector: MappingConnector,
  appConfig: AppConfig) {

  private[controllers] def completeMappingWhenAvailable(utr: Option[Utr])(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] = {

    val doMappingAnswer: Boolean = request.session.get("performAutoMapping").isDefined
    for (mappingIfConfirmed <- if (appConfig.autoMapAgentEnrolments && doMappingAnswer) {
                                utr
                                  .map(mappingConnector.updatePreSubscriptionWithArn)
                                  .getOrElse(Future successful ())
                              } else Future successful ())
      yield Redirect(routes.SubscriptionController.showSubscriptionComplete())
  }

  private[controllers] def handleAutoMapping(details: InitialDetails)(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Call] = {
    val hasAlreadyAnsweredAutoMapping: Boolean = request.session.get("performAutoMapping").isDefined
    if (appConfig.autoMapAgentEnrolments && !hasAlreadyAnsweredAutoMapping) {
      sessionStoreService.fetchMappingEligible.map(MappingEligibility.apply(_) match {
        case IsEligible => routes.SubscriptionController.showLinkClients()
        case _          => routes.SubscriptionController.showCheckAnswers()
      })
    } else Future successful routes.SubscriptionController.showCheckAnswers()
  }
  //
  //
  //    for (redirectLocation <- if (appConfig.autoMapAgentEnrolments) {
  //      sessionStoreService.fetchMappingEligible.map {
  //        MappingEligibility.apply(_) match {
  //          case IsEligible => routes.SubscriptionController.showLinkClients()
  //          case IsNotEligible => routes.SubscriptionController.showSubscriptionComplete()
  //          case UnknownEligibility => {
  //            Logger.warn("chainedSessionDetails did not cache wasEligibleForMapping")
  //            routes.SubscriptionController.showSubscriptionComplete()
  //          }
  //        }
  //      }
  //    } else Future successful routes.SubscriptionController.showSubscriptionComplete()
  //
  //
  //    ) yield Redirect(redirectLocation).withSession(request.session + ("arn" -> arn.value))
}
