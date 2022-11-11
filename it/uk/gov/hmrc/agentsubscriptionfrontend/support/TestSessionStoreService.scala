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

package uk.gov.hmrc.agentsubscriptionfrontend.support

import play.api.mvc.Request
import uk.gov.hmrc.agentsubscriptionfrontend.models.AgentSession
import uk.gov.hmrc.agentsubscriptionfrontend.service.MongoDBSessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.util._
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl._
import uk.gov.hmrc.play.bootstrap.binders.{RedirectUrl, UnsafePermitAll}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class TestSessionStoreService extends MongoDBSessionStoreService(null) {

  class Session(
    var continueUrl: Option[String] = None,
    var goBackUrl: Option[String] = None,
    var changingAnswers: Option[Boolean] = None,
    var agentSession: Option[AgentSession] = None)

  private val sessions = collection.mutable.Map[String, Session]()

  private def sessionKey(implicit req: Request[_]): String = HeaderCarrierConverter.fromRequestAndSession(req, req.session).sessionId match {
    case None         => "default"
    case Some(sessionId) => sessionId.toString
  }

  var currentSessionTest: SessionTest = NormalSession

  def currentSession(implicit req: Request[_]): Session =
    sessions.getOrElseUpdate(sessionKey, new Session())

  def clear(): Unit = {
    sessions.clear()
    currentSessionTest = NormalSession
  }

  def allSessionsRemoved: Boolean =
    sessions.isEmpty

  override def fetchContinueUrl(implicit req: Request[Any], ec: ExecutionContext): Future[Option[RedirectUrl]] =
    fetchFromSession(currentSession.continueUrl.map(c => RedirectUrl(c)))

  override def cacheContinueUrl(url: RedirectUrl)(implicit req: Request[Any], ec: ExecutionContext): Future[Unit] =
    Future.successful(currentSession.continueUrl = Some(url.get(UnsafePermitAll).url))

  override def cacheGoBackUrl(url: String)(implicit req: Request[Any], ec: ExecutionContext): Future[Unit] =
    (currentSession.goBackUrl = Some(url)).toFuture

  private def fetchFromSession[A](property: Option[A]): Future[Option[A]] =
    currentSessionTest match {
      case NormalSession => property.toFuture
      case SessionLost => Future.failed(new RuntimeException)
    }

  override def fetchGoBackUrl(implicit req: Request[Any], ec: ExecutionContext): Future[Option[String]] =
    fetchFromSession(currentSession.goBackUrl)

  override def cacheIsChangingAnswers(changing: Boolean)(implicit req: Request[Any], ec: ExecutionContext): Future[Unit] =
    (currentSession.changingAnswers = Some(true)).toFuture

  override def fetchIsChangingAnswers(implicit req: Request[Any], ec: ExecutionContext): Future[Option[Boolean]] =
    fetchFromSession(currentSession.changingAnswers)

  override def cacheAgentSession(agentSession: AgentSession)(implicit req: Request[Any], ec: ExecutionContext): Future[Unit] =
    (currentSession.agentSession = Some(agentSession)).toFuture

  override def fetchAgentSession(implicit req: Request[Any], ec: ExecutionContext): Future[Option[AgentSession]] =
    fetchFromSession(currentSession.agentSession)

  override def remove()(implicit req: Request[Any], ec: ExecutionContext): Future[Unit] = {
    sessions.clear()
    ().toFuture
  }


}
