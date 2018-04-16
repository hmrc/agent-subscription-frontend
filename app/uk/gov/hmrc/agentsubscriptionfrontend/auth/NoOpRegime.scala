/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.auth

import java.net.URLEncoder

import javax.inject.Inject
import play.api.{ Configuration, Environment }
import play.api.mvc.Request
import play.api.mvc.Results._
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.routes
import uk.gov.hmrc.agentsubscriptionfrontend.support.CallOps
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts
import uk.gov.hmrc.play.frontend.auth.{ GovernmentGateway, TaxRegime }

import scala.concurrent.Future

object NoOpRegime extends TaxRegime {
  override def isAuthorised(accounts: Accounts) = true

  override val authenticationType = CheckAgencyStatusGovernmentGateway
}

object CheckAgencyStatusGovernmentGateway extends GovernmentGateway {
  val configuration = Configuration.apply()
  override lazy val loginURL = s"${configuration.getString("authentication.government-gateway.sign-in.base-url")}${configuration.getString("authentication.government-gateway.sign-in.path")}"
  override lazy val continueURL = configuration.getString("authentication.login-callback.url") + routes.CheckAgencyController.showCheckAgencyStatus().url
}

object NoOpRegimeWithContinueUrl extends TaxRegime {
  override def isAuthorised(accounts: Accounts) = true

  override val authenticationType = new GovernmentGateway {
    val configuration = Configuration.apply()
    override lazy val loginURL = s"${configuration.getString("authentication.government-gateway.sign-in.base-url")}${configuration.getString("authentication.government-gateway.sign-in.path")}"
    override lazy val continueURL = configuration.getString("authentication.login-callback.url") + routes.CheckAgencyController.showCheckAgencyStatus().url

    override def redirectToLogin(implicit request: Request[_]) = {
      val url = CallOps.addParamsToUrl(continueURL, "continue" -> request.getQueryString("continue"))

      Future.successful(Redirect(loginURL, Map("continue" -> Seq(url), "origin" -> Seq(origin))))
    }
  }
}
