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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import javax.inject.{Inject, Singleton}

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.agentsubscriptionfrontend.auth.NoOpRegime
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.agentsubscriptionfrontend.config.{AppConfig, FrontendAuthConnector}
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.{Actions, AuthContext}
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future.successful

@Singleton
class SubscriptionController @Inject() (override val messagesApi: MessagesApi) (implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport with Actions{
  val showCheckAgencyStatus =
    AuthorisedFor(NoOpRegime, GGConfidence).async { implicit authContext: AuthContext =>implicit request =>
    successful(Ok(html.subscribe()))
  }

  val showSubscriptionDetails = Action.async { implicit request =>
    successful(Ok(html.subscription_details()))
  }

  override protected def authConnector: AuthConnector = FrontendAuthConnector
}
