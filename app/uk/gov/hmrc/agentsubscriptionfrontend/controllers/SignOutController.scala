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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.api.{Configuration, Environment}
import sttp.model.Uri.UriContext
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.service.{MongoDBSessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.timed_out
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SignOutController @Inject() (
  timedOutTemplate: timed_out,
  val sessionStoreService: MongoDBSessionStoreService,
  val redirectUrlActions: RedirectUrlActions,
  val metrics: Metrics,
  val authConnector: AuthConnector,
  val env: Environment,
  val config: Configuration,
  val subscriptionJourneyService: SubscriptionJourneyService,
  mcc: MessagesControllerComponents
)(implicit val appConfig: AppConfig, val ec: ExecutionContext, @Named("aes") val crypto: Encrypter with Decrypter)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  private def signOutWithContinue(continue: String) = {
    val signOutAndRedirectUrl: String = uri"""${appConfig.signOutUrl}?${Map("continue" -> continue)}""".toString
    Redirect(signOutAndRedirectUrl)
  }

  def redirectAgentToCreateCleanCreds: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      for {
        agentSubContinueUrlOpt <- sessionStoreService.fetchContinueUrl
        redirectUrl            <- redirectUrlActions.getUrl(agentSubContinueUrlOpt)
        continueId =
          agent.subscriptionJourneyRecord.flatMap(_.continueId)
      } yield {
        val continueFromGG = uri"${appConfig.returnAfterGGCredsCreatedUrl}?${Map(
            "id"       -> continueId,
            "continue" -> redirectUrl
          )}"
        val continueFromSignOut = uri"${appConfig.ggRegistrationFrontendExternalUrl}?${Map(
            "accountType" -> "agent",
            "origin"      -> "unknown",
            "continue"    -> continueFromGG.toString
          )}"

        signOutWithContinue(continueFromSignOut.toString)

      }
    }
  }

  def redirectToCreateCleanCreds: Action[AnyContent] = Action.async { implicit request =>
    for {
      agentSubContinueUrlOpt <- sessionStoreService.fetchContinueUrl
      redirectUrl            <- redirectUrlActions.getUrl(agentSubContinueUrlOpt)
    } yield {
      val continueFromGG = uri"${appConfig.returnAfterGGCredsCreatedUrl}?${Map(
          "continue" -> redirectUrl
        )}"
      val continueFromSignOut = uri"${appConfig.ggRegistrationFrontendExternalUrl}?${Map(
          "accountType" -> "agent",
          "origin"      -> "unknown",
          "continue"    -> continueFromGG.toString
        )}"

      signOutWithContinue(continueFromSignOut.toString)
    }
  }

  def signOutWithContinueUrl: Action[AnyContent] = Action.async { implicit request =>
    val result: Future[Result] = for {
      agentSubContinueUrlOpt <- sessionStoreService.fetchContinueUrl
      redirectUrl            <- redirectUrlActions.getUrl(agentSubContinueUrlOpt)
    } yield signOutWithContinue(redirectUrl.getOrElse(appConfig.returnAfterGGCredsCreatedUrl))

    result.recover { case _: RuntimeException =>
      startNewSession
    }
  }

  def startSurvey: Action[AnyContent] = Action {
    signOutWithContinue(appConfig.surveyRedirectUrl)
  }

  def redirectToASAccountPage: Action[AnyContent] = Action {
    signOutWithContinue(appConfig.agentServicesAccountUrl)
  }

  def signOut: Action[AnyContent] = Action {
    startNewSession
  }

  def timeOut: Action[AnyContent] = Action {
    val continue = uri"${appConfig.selfExternalUrl + routes.SignOutController.timedOut().url}"
    signOutWithContinue(continue.toString)
  }

  def timedOut: Action[AnyContent] = Action.async { implicit request =>
    Future successful Ok(timedOutTemplate())
  }

  private def startNewSession: Result = {
    val continue = uri"${appConfig.selfExternalUrl + routes.TaskListController.showTaskList().url}"
    signOutWithContinue(continue.toString)
  }

  def redirectToBusinessTypeForm: Action[AnyContent] = Action {
    val continue = uri"${appConfig.selfExternalUrl + routes.BusinessTypeController.showBusinessTypeForm().url}"
    signOutWithContinue(continue.toString)
  }
}
