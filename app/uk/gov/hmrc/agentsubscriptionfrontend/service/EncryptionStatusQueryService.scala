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

import com.google.inject.Inject
import org.mongodb.scala.model.Filters.exists
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.MongoComponent

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EncryptionStatusQueryService @Inject() (
  config: Configuration,
  mongo: MongoComponent
)(implicit ec: ExecutionContext)
    extends Logging {

  private def totalAgentSessions(): Future[Long] =
    mongo.database.getCollection("sessions").countDocuments(exists("data.agentSession")).toFuture()

  private def totalAgentSessionsWithEncryption(): Future[Long] =
    mongo.database.getCollection("sessions").countDocuments(exists("data.agentSession.encrypted")).toFuture()

  private def queryEncryptionStatus(): Future[Unit] =
    for {
      agentSessionsWithEncryption <- totalAgentSessionsWithEncryption()
      agentSessions               <- totalAgentSessions()
      leftToEncrypt = agentSessions - agentSessionsWithEncryption
    } yield logger.warn(
      "[AgentSessionEncryptionStatus] Total agent sessions saved = " + agentSessions +
        ", total agent sessions with encryption = " + agentSessionsWithEncryption +
        ", there are " + leftToEncrypt + " agent sessions left to encrypt or expire."
    )

  queryEncryptionStatus().recover { case e: Throwable =>
    logger.error("Encryption status query failed", e)
  }

}
