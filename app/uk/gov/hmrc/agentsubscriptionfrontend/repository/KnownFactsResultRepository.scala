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

package uk.gov.hmrc.agentsubscriptionfrontend.repository

import java.util.UUID

import play.api.libs.json.Json
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait KnownFactsResultRepository {
  def findKnownFactsResult(uuid: String)(implicit hc: HeaderCarrier): Future[Option[KnownFactsResult]]

  def create(knownFactsResult: KnownFactsResult)(implicit hc: HeaderCarrier): Future[String]
}

class KnownFactsResultMongoRepository(mongo: () => DB)
  extends ReactiveRepository[StashedKnownFactsResult, BSONObjectID]("agent-known-facts-results", mongo, StashedKnownFactsResult.format, ReactiveMongoFormats.objectIdFormats)
  with KnownFactsResultRepository with Repository[StashedKnownFactsResult, BSONObjectID] {

  override def indexes: Seq[Index] =
    Seq(Index(
      key = Seq("uuid" -> IndexType.Ascending),
      name = Some("uuidUnique"),
      unique = true,
      options = BSONDocument("expireAfterSeconds" -> 60))) //FIXME: Retrieve TTL from config

  override def findKnownFactsResult(uuid: String)(implicit hc: HeaderCarrier): Future[Option[KnownFactsResult]] = {
    collection.find(Json.obj("uuid" -> uuid)).one[StashedKnownFactsResult].map {
      maybeStashedResult => maybeStashedResult.map(_.knownFactsResult)
    }
  }

  override def create(knownFactsResult: KnownFactsResult)(implicit hc: HeaderCarrier): Future[String] = {
    val uuid: String = UUID.randomUUID().toString
    super.insert(StashedKnownFactsResult(uuid, knownFactsResult)).map { _ => uuid }
  }
}

case class StashedKnownFactsResult(uuid: String, knownFactsResult: KnownFactsResult)

object StashedKnownFactsResult {
  implicit val format = Json.format[StashedKnownFactsResult]
}
