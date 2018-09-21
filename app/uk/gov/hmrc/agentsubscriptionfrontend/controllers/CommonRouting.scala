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

import com.kenshoo.play.metrics.Metrics
import play.api.mvc._
import play.api.mvc.Results._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.MappingConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.{InitialDetails, MappingEligibility}
import uk.gov.hmrc.agentsubscriptionfrontend.models.MappingEligibility.{IsEligible, IsNotEligible, UnknownEligibility}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SubscriptionService
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CommonRouting @Inject()(
  mappingConnector: MappingConnector,
  val metrics: Metrics,
  subscriptionService: SubscriptionService,
  appConfig: AppConfig)
    extends Monitoring {

  private[controllers] def completeMappingWhenAvailable(utr: Utr, completedPartialSub: Boolean = false)(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] = {

    val doMappingAnswer: Boolean = request.session.get("performAutoMapping").isDefined || completedPartialSub

    for {
      completeMapping <- if (appConfig.autoMapAgentEnrolments && doMappingAnswer)
                          mappingConnector.updatePreSubscriptionWithArn(utr)
                        else Future successful ()
    } yield Redirect(routes.SubscriptionController.showSubscriptionComplete())
  }

  private[controllers] def handlePartialSubscription(
    kfcUtr: Utr,
    kfcPostcode: String,
    eligibleForMapping: Option[Boolean])(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] =
    MappingEligibility.apply(eligibleForMapping) match {
      case IsEligible =>
        Future successful Redirect(routes.SubscriptionController.showLinkClients())
          .withSession(request.session + ("isPartiallySubscribed" -> "true"))
      case _ => {
        subscriptionService
          .completePartialSubscription(kfcUtr, kfcPostcode)
          .map { _ =>
            Redirect(routes.SubscriptionController.showSubscriptionComplete())
          }
      }

    }

  private[controllers] def handleAutoMapping(eligibleForMapping: Option[Boolean])(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] =
    if (appConfig.autoMapAgentEnrolments) {
      MappingEligibility.apply(eligibleForMapping) match {
        case IsEligible => Future successful Redirect(routes.SubscriptionController.showLinkClients())
        case _          => Future successful Redirect(routes.SubscriptionController.showCheckAnswers())
      }
    } else Future successful Redirect(routes.SubscriptionController.showCheckAnswers())
}
