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

import javax.inject.Inject
import play.api.Logger
import play.api.mvc.{Result, Results}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{InitialDetails, KnownFactsResult}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait SessionDataMissing {
  this: Results =>

  val sessionStoreService: SessionStoreService

  def sessionMissingRedirect(): Result = {
    Logger(getClass).warn(
      "No KnownFactsResult and/or InitialDetails in session store, redirecting back to check-business-type")
    Redirect(routes.CheckAgencyController.showCheckBusinessType())
  }

  def withInitialDetails(body: InitialDetails => Future[Result])
                        (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    withModelFromSession[InitialDetails](sessionStoreService.fetchInitialDetails)(body)

  def withKnownFactsResult(body: KnownFactsResult => Future[Result])
                          (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    withModelFromSession[KnownFactsResult](sessionStoreService.fetchKnownFactsResult)(body)

  private def withModelFromSession[T](sessionStoreRetrieval: => Future[Option[T]])(
    bodyRequiringModel: T => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    sessionStoreRetrieval.flatMap { retrievedModelOpt =>
      retrievedModelOpt.map(bodyRequiringModel).getOrElse(Future.successful(sessionMissingRedirect()))
    }

}
