package uk.gov.hmrc.agentsubscriptionfrontend.support

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.OneServerPerSuite
import uk.gov.hmrc.mongo.{MongoSpecSupport, Awaiting => MongoAwaiting}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

trait MongoApp extends MongoSpecSupport with ResetMongoBeforeTest with OneServerPerSuite {
  me: Suite =>

  protected def mongoConfiguration = Map("mongodb.uri" -> mongoUri)
}

trait ResetMongoBeforeTest extends BeforeAndAfterEach {
  me: Suite with MongoSpecSupport =>

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
  }

  def dropMongoDb()(implicit ec: ExecutionContext = global): Unit = {
    Awaiting.await(mongo().drop())
  }
}

object Awaiting extends MongoAwaiting