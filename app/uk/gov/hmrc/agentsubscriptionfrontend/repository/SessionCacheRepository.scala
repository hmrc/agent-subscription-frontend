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

package uk.gov.hmrc.agentsubscriptionfrontend.repository

import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}
import uk.gov.hmrc.mongo.cache.{SessionCacheRepository => CacheRepository}
import uk.gov.hmrc.mongo.play.json.Codecs

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import uk.gov.hmrc.agentsubscriptionfrontend.util.EncryptionUtils.encryptedStringFormat

@Singleton
class SessionCacheRepository @Inject() (mongo: MongoComponent, timestampSupport: TimestampSupport, @Named("aes") crypto: Encrypter with Decrypter)(
  implicit ec: ExecutionContext
) extends CacheRepository(
      mongoComponent = mongo,
      collectionName = "sessions",
      replaceIndexes = true,
      ttl = 15.minutes,
      timestampSupport = timestampSupport,
      sessionIdKey = SessionKeys.sessionId,
      extraCodecs = Seq(Codecs.playFormatCodec(encryptedStringFormat(crypto)))
    )
