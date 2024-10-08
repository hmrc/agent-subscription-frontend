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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import play.api.libs.json.{Json, OFormat}
import play.api.mvc.Request
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, AmlsSession}
import uk.gov.hmrc.agentsubscriptionfrontend.repository.{SessionCache, SessionCacheRepository}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MongoDBSessionStoreService @Inject() (sessionCache: SessionCacheRepository) {

  implicit val format: OFormat[RedirectUrl] = Json.format[RedirectUrl]

  val continueUrlCache: SessionCache[RedirectUrl] = new SessionCache[RedirectUrl] {
    override val sessionName: String = "continueUrl"
    override val cacheRepository: SessionCacheRepository = sessionCache
  }

  val goBackUrlCache: SessionCache[String] = new SessionCache[String] {
    override val sessionName: String = "goBackUrl"
    override val cacheRepository: SessionCacheRepository = sessionCache
  }

  val isChangingAnswersCache: SessionCache[Boolean] = new SessionCache[Boolean] {
    override val sessionName: String = "isChangingAnswers"
    override val cacheRepository: SessionCacheRepository = sessionCache
  }

  val agentSessionCache: SessionCache[AgentSession] = new SessionCache[AgentSession] {
    override val sessionName: String = "agentSession"
    override val cacheRepository: SessionCacheRepository = sessionCache
  }

  val amlsSessionCache: SessionCache[AmlsSession] = new SessionCache[AmlsSession] {
    override val sessionName: String = "amlsSession"
    override val cacheRepository: SessionCacheRepository = sessionCache
  }

  def fetchContinueUrl(implicit request: Request[Any], ec: ExecutionContext): Future[Option[RedirectUrl]] =
    continueUrlCache.fetch

  def cacheContinueUrl(url: RedirectUrl)(implicit request: Request[Any], ec: ExecutionContext): Future[Unit] =
    continueUrlCache.save(url).map(_ => ())

  def cacheGoBackUrl(url: String)(implicit request: Request[Any], ec: ExecutionContext): Future[Unit] =
    goBackUrlCache.save(url).map(_ => ())

  def fetchGoBackUrl(implicit request: Request[Any], ec: ExecutionContext): Future[Option[String]] =
    goBackUrlCache.fetch

  def cacheIsChangingAnswers(changing: Boolean)(implicit request: Request[Any], ec: ExecutionContext): Future[Unit] =
    isChangingAnswersCache.save(changing).map(_ => ())

  def fetchIsChangingAnswers(implicit request: Request[Any], ec: ExecutionContext): Future[Option[Boolean]] =
    isChangingAnswersCache.fetch

  def cacheAgentSession(
    agentSession: AgentSession
  )(implicit request: Request[Any], ec: ExecutionContext, crypto: Encrypter with Decrypter): Future[Unit] =
    agentSessionCache.save(agentSession)(request, AgentSession.databaseFormat(crypto), ec).map(_ => ())

  def fetchAgentSession(implicit request: Request[Any], ec: ExecutionContext, crypto: Encrypter with Decrypter): Future[Option[AgentSession]] =
    agentSessionCache.fetch(request, AgentSession.databaseFormat(crypto), ec)

  def cacheAmlsSession(amlsSession: AmlsSession)(implicit request: Request[Any], ec: ExecutionContext): Future[Unit] =
    amlsSessionCache.save(amlsSession).map(_ => ())

  def fetchAmlsSession(implicit request: Request[Any], ec: ExecutionContext): Future[Option[AmlsSession]] =
    amlsSessionCache.fetch

  def remove()(implicit request: Request[Any], ec: ExecutionContext): Future[Unit] =
    agentSessionCache.delete().map(_ => ())
}
