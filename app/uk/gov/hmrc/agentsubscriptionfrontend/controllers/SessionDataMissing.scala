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

import play.api.Logger
import play.api.mvc.{AnyContent, Request, Result, Results}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscriptionfrontend.models.{InitialDetails, KnownFactsResult}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait SessionDataMissing {
  this: Results =>

  val sessionStoreService: SessionStoreService

  def withArnFromSession(
    body: Arn => Future[Result])(implicit request: Request[AnyContent], ec: ExecutionContext): Future[Result] =
    request.session.get("arn").fold(Future.successful(sessionMissingRedirect("ARN")))(arn => body(Arn(arn)))

  def withInitialDetails(
    body: InitialDetails => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    withModelFromSessionStore[InitialDetails]("InitialDetails", sessionStoreService.fetchInitialDetails)(body)

  def withKnownFactsResult(
    body: KnownFactsResult => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    withModelFromSessionStore[KnownFactsResult]("KnownFactsResult", sessionStoreService.fetchKnownFactsResult)(body)

  private def withModelFromSessionStore[T](modelName: String, sessionStoreRetrieval: => Future[Option[T]])(
    bodyRequiringModel: T => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    sessionStoreRetrieval.flatMap { retrievedModelOpt =>
      retrievedModelOpt.map(bodyRequiringModel).getOrElse(Future.successful(sessionMissingRedirect(modelName)))
    }

  private def sessionMissingRedirect(missingSessionItem: String): Result = {
    Logger(getClass).warn(
      s"Missing $missingSessionItem in session or keystore, redirecting back to /check-business-type")
    Redirect(routes.CheckAgencyController.showBusinessTypeForm())
  }
}
