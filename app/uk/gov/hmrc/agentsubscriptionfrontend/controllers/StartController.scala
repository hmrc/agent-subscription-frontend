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

import javax.inject.Inject

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.NoOpRegime
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.repository.KnownFactsResultMongoRepository
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.passcode.authentication.{PasscodeAuthentication, PasscodeAuthenticationProvider, PasscodeVerificationConfig}
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class StartController @Inject()(override val messagesApi: MessagesApi,
                                override val authConnector: AuthConnector,
                                override val config: PasscodeVerificationConfig,
                                override val passcodeAuthenticationProvider: PasscodeAuthenticationProvider,
                                knownFactsResultMongoRepository: KnownFactsResultMongoRepository,
                                continueUrlActions: ContinueUrlActions,
                                sessionStoreService: SessionStoreService)
                               (implicit appConfig: AppConfig)
    extends FrontendController with I18nSupport with Actions with PasscodeAuthentication {

  import continueUrlActions._

  val root: Action[AnyContent] = PasscodeAuthenticatedActionAsync { implicit request =>
    withMaybeContinueUrlCached {
      Redirect(routes.StartController.start())
    }
  }

  def start: Action[AnyContent] = PasscodeAuthenticatedActionAsync { implicit request =>
    withMaybeContinueUrlCached {
      Ok(html.start())
    }
  }

  val showNonAgentNextSteps: Action[AnyContent] = AuthorisedFor(NoOpRegime, GGConfidence) { implicit authContext =>
    implicit request =>
      Ok(html.non_agent_next_steps())
  }

  def returnAfterGGCredsCreated(id: Option[String] = None): Action[AnyContent] = PasscodeAuthenticatedActionAsync { implicit request =>
    withMaybeContinueUrlCachedAsync {
      id match {
        case Some(knownFactsId) =>
          for {
            knownFactsResultOpt <- knownFactsResultMongoRepository.findKnownFactsResult(knownFactsId)
            _ <- knownFactsResultMongoRepository.delete(knownFactsId)
            _ <- knownFactsResultOpt match {
              case Some(knownFacts) => sessionStoreService.cacheKnownFactsResult(knownFacts)
              case None => Future.successful(())
            }
          } yield {
            knownFactsResultOpt match {
              case Some(_) => Redirect(routes.SubscriptionController.showSubscriptionDetails())
              case None => Redirect(routes.CheckAgencyController.checkAgencyStatus())
            }
          }
        case None =>
          Future.successful(Redirect(routes.CheckAgencyController.checkAgencyStatus()))
      }
    }
  }
}
