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

package uk.gov.hmrc.agentsubscriptionfrontend.config

import java.util.Collections
import javax.inject.Singleton

import play.api.Play.{configuration, current}
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.routes
import uk.gov.hmrc.play.config.ServicesConfig
import scala.collection.JavaConversions._

trait AppConfig {
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String
  val betaFeedbackUrl: String
  val betaFeedbackUnauthenticatedUrl: String
  val governmentGatewayUrl: String
  val blacklistedPostcodes: Set[String]
  val journeyName: String
  val agentServicesAccountUrl: String
  val domainWhiteList: Set[String]
}

trait StrictConfig {
  def loadConfig(key: String): String = configuration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))
}

object GGConfig extends StrictConfig {
  private lazy val ggBaseUrl = loadConfig("authentication.government-gateway.sign-in.base-url")
  lazy val ggSignInUrl: String = {
    val ggSignInPath = loadConfig("authentication.government-gateway.sign-in.path")
    s"$ggBaseUrl$ggSignInPath"
  }

  lazy val checkAgencyStatusCallbackUrl: String = loadConfig("authentication.login-callback.url") +
    routes.CheckAgencyController.showCheckAgencyStatus().url
}

@Singleton
class FrontendAppConfig extends AppConfig with StrictConfig with ServicesConfig {
  private lazy val contactHost = runModeConfiguration.getString(s"contact-frontend.host").getOrElse("")
  private lazy val servicesAccountUrl = getConfString("agent-services-account-frontend.external-url", "")
  private lazy val servicesAccountPath = getConfString("agent-services-account-frontend.start.path", "")
  private val contactFormServiceIdentifier = "AOSS"

  override lazy val analyticsToken: String = loadConfig(s"google-analytics.token")
  override lazy val analyticsHost: String = loadConfig(s"google-analytics.host")
  override lazy val betaFeedbackUrl = s"$contactHost/contact/beta-feedback?service=$contactFormServiceIdentifier"
  override lazy val betaFeedbackUnauthenticatedUrl = s"$contactHost/contact/beta-feedback-unauthenticated?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemPartialUrl = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  override lazy val governmentGatewayUrl: String = loadConfig("government-gateway.url")
  override lazy val blacklistedPostcodes: Set[String] =
    PostcodesLoader.load("/po_box_postcodes_abp_49.csv").map(x => x.toUpperCase.replace(" ", "")).toSet
  override lazy val journeyName: String = getConfString("address-lookup-frontend.journeyName", "")
  override lazy val agentServicesAccountUrl: String = s"$servicesAccountUrl/$servicesAccountPath"

  override lazy val domainWhiteList =
    runModeConfiguration.getStringList("continueUrl.domainWhiteList").getOrElse(Collections.emptyList()).toSet

}
