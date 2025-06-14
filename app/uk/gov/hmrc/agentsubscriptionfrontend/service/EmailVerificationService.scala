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

import play.api.Logging
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.{Email, EmailVerificationStatus, VerifyEmailRequest}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailVerificationService @Inject() (emailVerificationConnector: EmailVerificationConnector) extends Logging {

  def verifyEmail(
    credId: String,
    mEmail: Option[Email],
    continueUrl: String,
    mBackUrl: Option[String],
    accessibilityStatementUrl: String,
    lang: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    for {
      mVerifyEmailResponse <- emailVerificationConnector.verifyEmail(
                                VerifyEmailRequest(
                                  credId = credId,
                                  continueUrl = continueUrl,
                                  origin = if (lang == "cy") "Gwasanaethau Asiant CThEM" else "HMRC Agent Services",
                                  deskproServiceName = None,
                                  accessibilityStatementUrl = accessibilityStatementUrl,
                                  email = mEmail,
                                  lang = Some(lang),
                                  backUrl = mBackUrl,
                                  pageTitle = None
                                )
                              )
    } yield mVerifyEmailResponse.map(_.redirectUri)

  def checkStatus(credId: String, email: String)(implicit rh: RequestHeader, ec: ExecutionContext): Future[EmailVerificationStatus] = {
    val emailLowerCase = email.toLowerCase
    emailVerificationConnector.checkEmail(credId).map {
      case Some(vsr) if vsr.emails.filter(_.emailAddress == emailLowerCase).exists(_.verified) =>
        EmailVerificationStatus.Verified
      case Some(vsr) if vsr.emails.filter(_.emailAddress == emailLowerCase).exists(_.locked) => EmailVerificationStatus.Locked
      case Some(x)                                                                           => EmailVerificationStatus.Unverified
      case None                                                                              => EmailVerificationStatus.Error
    }
  }

}
