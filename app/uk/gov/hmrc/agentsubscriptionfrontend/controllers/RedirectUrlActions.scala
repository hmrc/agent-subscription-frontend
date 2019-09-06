/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.SsoConnector
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl._
import uk.gov.hmrc.play.bootstrap.binders.{AbsoluteWithHostnameFromWhitelist, RedirectUrl, UnsafePermitAll}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class RedirectUrlActions @Inject()(sessionStoreService: SessionStoreService, ssoConnector: SsoConnector)(
  implicit executor: ExecutionContext) {

  def extractRedirectUrl[A](implicit request: Request[A], hc: HeaderCarrier): Option[RedirectUrl] =
    request.getQueryString("continue") match {
      case Some(redirectUrl) =>
        Try(RedirectUrl(redirectUrl)) match {
          case Success(url) => Some(url)
          case Failure(e) =>
            Logger.warn(s"[$redirectUrl] is not a valid redirect URL, $e")
            None
        }
      case None =>
        None
    }

  def withMaybeRedirectUrl[A](
    block: Option[String] => Future[Result])(implicit request: Request[A], hc: HeaderCarrier): Future[Result] = {
    val whitelistPolicy = AbsoluteWithHostnameFromWhitelist(ssoConnector.getWhitelistedDomains())

    val redirectUrlOpt: Future[Option[RedirectUrl]] = extractRedirectUrl
    redirectUrlOpt.flatMap {
      case Some(redirectUrl) =>
        val unsafeUrl = redirectUrl.get(UnsafePermitAll).url
        if (RedirectUrl.isRelativeUrl(unsafeUrl)) block(Some(unsafeUrl))
        else
          redirectUrl.getEither(whitelistPolicy).flatMap {
            case Right(safeRedirectUrl) => block(Some(safeRedirectUrl.url))
            case Left(errorMessage) =>
              Logger.warn(s"url does not comply with whitelist policy, removing redirect url... $errorMessage")
              block(None)
          }
      case None => block(None)
    }
  }

  def withMaybeRedirectUrlCached[A](
    block: => Future[Result])(implicit hc: HeaderCarrier, request: Request[A]): Future[Result] =
    withMaybeRedirectUrl {
      case None => block
      case Some(url) =>
        sessionStoreService.cacheContinueUrl(url).flatMap(_ => block)
    }
}
