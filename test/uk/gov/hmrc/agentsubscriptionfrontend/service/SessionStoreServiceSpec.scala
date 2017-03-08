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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import play.api.libs.json.{JsValue, Reads, Writes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.http.cache.client.{CacheMap, NoSessionException, SessionCache}
import uk.gov.hmrc.play.http.logging.SessionId
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SessionStoreServiceSpec extends UnitSpec {

  private implicit val hc = HeaderCarrier(sessionId = Some(SessionId("sessionId123456")))

  "SessionStoreService" should {
    "store known facts" in {
      val store = new SessionStoreService(new TestSessionCache())

      val knownFactsResult = KnownFactsResult("9876543210", "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)

      await(store.cacheKnownFactsResult(knownFactsResult))

      await(store.fetchKnownFactsResult) shouldBe Some(knownFactsResult)
    }

    "return None when no known facts have been stored" in {
      val store = new SessionStoreService(new TestSessionCache())

      await(store.fetchKnownFactsResult) shouldBe None
    }

    "remove the underlying storage for the current session when remove is called" in {
      val store = new SessionStoreService(new TestSessionCache())

      val knownFactsResult = KnownFactsResult("9876543210", "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)

      await(store.cacheKnownFactsResult(knownFactsResult))

      await(store.remove())

      await(store.fetchKnownFactsResult) shouldBe None
    }
  }

}

class TestSessionCache extends SessionCache {
  override def defaultSource = ???
  override def baseUri = ???
  override def domain = ???
  override def http = ???

  private val store = mutable.Map[String, JsValue]()

  private val noSession = Future.failed[String](NoSessionException)

  private def testCacheId(implicit hc: HeaderCarrier): Future[String] =
    hc.sessionId.fold(noSession)(c => Future.successful(c.value))

  override def cache[A](formId: String, body: A)(implicit wts: Writes[A], hc: HeaderCarrier): Future[CacheMap] =
    testCacheId.map { c =>
      store.put(formId, wts.writes(body))
      CacheMap(c, store.toMap)
    }

  override def fetch()(implicit hc: HeaderCarrier): Future[Option[CacheMap]] =
    testCacheId.map(c => Some(CacheMap(c, store.toMap)))

  override def fetchAndGetEntry[T](key: String)(implicit hc: HeaderCarrier, rds: Reads[T]): Future[Option[T]] =
    Future {
      store.get(key).flatMap(jsValue => rds.reads(jsValue).asOpt)
    }

  override def remove()(implicit hc: HeaderCarrier): Future[HttpResponse] =
    Future {
      store.clear()
      null
    }
}
