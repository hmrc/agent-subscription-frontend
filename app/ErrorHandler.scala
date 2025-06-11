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

import com.google.inject.name.Named
import play.api._
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.Results._
import play.api.mvc.{Request, RequestHeader, Result}
import play.twirl.api.Html
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.util.AuthRedirects
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.{ErrorTemplate, ErrorTemplate5xx}
import uk.gov.hmrc.http.{JsValidationException, NotFoundException}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent
import uk.gov.hmrc.play.bootstrap.frontend.http.FrontendErrorHandler
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ErrorHandler @Inject() (
  val env: Environment,
  val messagesApi: MessagesApi,
  val auditConnector: AuditConnector,
  errorTemplate: ErrorTemplate,
  errorTemplate5xx: ErrorTemplate5xx,
  @Named("appName") val appName: String
)(implicit val config: Configuration, val ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendErrorHandler with AuthRedirects with ErrorAuditing with Logging {

  def theLogger: Logger = this.logger // for testing

  override def resolveError(request: RequestHeader, exception: Throwable): Future[Result] = {
    implicit val r = Request(request, "")
    logger.error(s"resolveError", exception)
    Future.successful(Ok(errorTemplate5xx()))
  }

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit
    request: RequestHeader
  ): Future[Html] =
    Future.successful(errorTemplate(Messages(pageTitle), Messages(heading), Messages(message)))

  override def internalServerErrorTemplate(implicit request: RequestHeader): Future[Html] =
    Future.successful(errorTemplate5xx())
}

object EventTypes {

  val RequestReceived: String = "RequestReceived"
  val TransactionFailureReason: String = "transactionFailureReason"
  val ServerInternalError: String = "ServerInternalError"
  val ResourceNotFound: String = "ResourceNotFound"
  val ServerValidationError: String = "ServerValidationError"
}

trait ErrorAuditing extends HttpAuditEvent {

  import EventTypes._

  def auditConnector: AuditConnector

  private val unexpectedError = "Unexpected error"
  private val notFoundError = "Resource Endpoint Not Found"
  private val badRequestError = "Request bad format exception"

  def auditServerError(request: RequestHeader, ex: Throwable)(implicit ec: ExecutionContext): Unit = {
    val eventType = ex match {
      case _: NotFoundException     => ResourceNotFound
      case _: JsValidationException => ServerValidationError
      case _                        => ServerInternalError
    }
    val transactionName = ex match {
      case _: NotFoundException => notFoundError
      case _                    => unexpectedError
    }
    auditConnector.sendEvent(
      dataEvent(eventType, transactionName, request, Map(TransactionFailureReason -> ex.getMessage))(
        HeaderCarrierConverter.fromRequestAndSession(request, request.session)
      )
    )
    ()
  }

  def auditClientError(request: RequestHeader, statusCode: Int, message: String)(implicit ec: ExecutionContext): Unit = {
    import play.api.http.Status._
    statusCode match {
      case NOT_FOUND =>
        auditConnector.sendEvent(
          dataEvent(ResourceNotFound, notFoundError, request, Map(TransactionFailureReason -> message))(
            HeaderCarrierConverter.fromRequestAndSession(request, request.session)
          )
        )
      case BAD_REQUEST =>
        auditConnector.sendEvent(
          dataEvent(ServerValidationError, badRequestError, request, Map(TransactionFailureReason -> message))(
            HeaderCarrierConverter.fromRequestAndSession(request, request.session)
          )
        )
      case _ =>
    }
    ()
  }
}
