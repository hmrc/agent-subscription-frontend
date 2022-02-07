/*
 * Copyright 2022 HM Revenue & Customs
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

import com.kenshoo.play.metrics.Metrics
import play.api.{Configuration, Environment}
import play.api.i18n.I18nSupport
import play.api.mvc.{Call, MessagesControllerComponents, RequestHeader}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.service.SubscriptionJourneyService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.hmrcfrontend.config.AccessibilityStatementConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.service.{EmailVerificationService, MongoDBSessionStoreService}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class RelevantState(subscriptionJourneyRecord: SubscriptionJourneyRecord, isChangingAnswers: Option[Boolean])

@Singleton
class EmailVerificationController @Inject()(
  env: Environment,
  val config: Configuration,
  val metrics: Metrics,
  val sessionStoreService: MongoDBSessionStoreService,
  val redirectUrlActions: RedirectUrlActions,
  val authConnector: AuthConnector,
  emailVerificationService: EmailVerificationService,
  val controllerComponents: MessagesControllerComponents,
  val subscriptionJourneyService: SubscriptionJourneyService,
  accessibilityStatementConfig: AccessibilityStatementConfig
)(implicit val appConfig: AppConfig, ec: ExecutionContext)
    extends GenericEmailVerificationController[RelevantState](env, emailVerificationService) with AuthActions with I18nSupport {

  override def emailVerificationEnabled: Boolean = !appConfig.disableEmailVerification

  override def emailVerificationFrontendBaseUrl: String = appConfig.emailVerificationFrontendBaseUrl

  override def accessibilityStatementUrl(implicit request: RequestHeader): String = accessibilityStatementConfig.url.getOrElse("")

  override def getState(implicit hc: HeaderCarrier): Future[(RelevantState, String)] =
    for {
      authProviderId    <- retrieveCredentials.map(_.getOrElse(throw new RuntimeException("Email verification: No credentials could be retrieved")))
      isChangingAnswers <- sessionStoreService.fetchIsChangingAnswers
      subscriptionJourneyRecord <- subscriptionJourneyService
                                    .getJourneyRecord(AuthProviderId(authProviderId.providerId))
                                    .map(_.getOrElse(
                                      throw new RuntimeException("Email verification: No subscription journey record could be retrieved")))
    } yield (RelevantState(subscriptionJourneyRecord, isChangingAnswers), authProviderId.providerId)

  override def getEmailToVerify(session: RelevantState): String =
    session.subscriptionJourneyRecord.contactEmailData.flatMap(_.contactEmail).getOrElse {
      throw new IllegalStateException("A verify email call has been made but no email to verify is present.")
    }

  override def isAlreadyVerified(session: RelevantState, email: String): Boolean = session.subscriptionJourneyRecord.verifiedEmails.contains(email)

  override def markEmailAsVerified(session: RelevantState, email: String)(implicit hc: HeaderCarrier): Future[RelevantState] = {
    val updatedVerifiedEmails = session.subscriptionJourneyRecord.verifiedEmails + email
    val updatedSjr = session.subscriptionJourneyRecord.copy(verifiedEmails = updatedVerifiedEmails)
    subscriptionJourneyService.saveJourneyRecord(updatedSjr).map(_ => session.copy(subscriptionJourneyRecord = updatedSjr))
  }

  override def selfRoute: Call = routes.EmailVerificationController.verifyEmail()
  override def redirectUrlIfVerified(session: RelevantState): Call =
    if (session.isChangingAnswers.getOrElse(false)) routes.SubscriptionController.showCheckAnswers()
    else routes.TaskListController.showTaskList()
  override def redirectUrlIfLocked(session: RelevantState): Call = routes.SubscriptionController.showCannotVerifyEmail()
  override def redirectUrlIfError(session: RelevantState): Call = routes.SubscriptionController.showCannotVerifyEmail()
  override def backLinkUrl(session: RelevantState): Option[Call] = Some(routes.ContactDetailsController.showContactEmailCheck())
  override def enterEmailUrl(session: RelevantState): Call = routes.ContactDetailsController.showContactEmailCheck()
}
