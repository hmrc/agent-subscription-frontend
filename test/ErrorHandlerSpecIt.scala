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

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{LogCapturing, UnitSpec}

class ErrorHandlerSpecIt extends UnitSpec with GuiceOneServerPerSuite with LogCapturing with ScalaFutures {

  val handler: ErrorHandler = app.injector.instanceOf[ErrorHandler]

  "ErrorHandler should show the error page" when {

    "a client error (400) occurs with log" in {
      withCaptureOfLoggingFrom(handler.theLogger) { logEvents =>
        val result = handler.onClientError(FakeRequest(), BAD_REQUEST, "some error")
        Thread.sleep(2000)
        status(result) shouldBe BAD_REQUEST
      }
    }
  }

}
