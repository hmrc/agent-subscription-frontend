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

package uk.gov.hmrc.agentsubscriptionfrontend.repository

import com.mongodb.MongoWriteException
import play.api.Logging
import play.api.libs.json._
import play.api.mvc.Request
import uk.gov.hmrc.agentsubscriptionfrontend.models.AgentSession
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.{ExecutionContext, Future}

trait MongoSessionStore[T] extends Logging {

  val sessionName: String
  val cacheRepository: SessionCacheRepository

  def get(implicit reads: Reads[T], request: Request[Any], ec: ExecutionContext): Future[Either[String, Option[T]]] =
    cacheRepository
      .getFromSession[T](DataKey[T](sessionName))
      .map {
        case Some(agentSession) => Right(Some(agentSession))
        case None               => Right(None)
      }
      .recover {
        case e ⇒
          Left(e.getMessage)
      }

  def store(newSession: T)(implicit writes: Writes[T], request: Request[Any], ec: ExecutionContext): Future[Either[String, Unit]] =
    cacheRepository
      .putSession[T](DataKey[T](sessionName), newSession)
      .map(_ => Right(()))
      .recover {
        case e: MongoWriteException => Left(e.getError.getMessage)
      }

  def delete()(implicit request: Request[Any], ec: ExecutionContext): Future[Either[String, Unit]] =
    cacheRepository
      .deleteFromSession[AgentSession](DataKey[AgentSession]("agentSession"))
      .map(_ => Right(()))
      .recover {
        case e: MongoWriteException => Left(e.getError.getMessage)
      }
}
