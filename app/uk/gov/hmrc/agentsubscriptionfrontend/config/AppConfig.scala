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

package uk.gov.hmrc.agentsubscriptionfrontend.config

import javax.inject.{ Inject, Singleton }
import java.util.Collections.emptyList

import com.google.inject.ImplementedBy
import play.api.{ Configuration, Environment }
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader
import uk.gov.hmrc.play.config.ServicesConfig

import scala.collection.JavaConversions._

trait AppConfig {
  val environment: Environment
  val configuration: Configuration
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
  val agentAssuranceFlag: Boolean
  val addressLookupContinueUrl: String
  val surveyRedirectUrl: String
  val sosRedirectUrl: String
  val mongoDbKnownFactsResult: Int
  val cacheableSessionDomain: String
}

@Singleton
class FrontendAppConfig @Inject() (val environment: Environment, val configuration: Configuration) extends AppConfig with ServicesConfig {

  override val runModeConfiguration: Configuration = configuration
  override protected def mode = environment.mode

  private val contactHost = runModeConfiguration.getString(s"contact-frontend.host").getOrElse("")
  private val servicesAccountUrl = getConfString("agent-services-account-frontend.external-url", "")
  private val servicesAccountPath = getConfString("agent-services-account-frontend.start.path", "")
  private val contactFormServiceIdentifier = "AOSS"

  def loadConfig(key: String): String = configuration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  override val analyticsToken: String = loadConfig(s"google-analytics.token")
  override val analyticsHost: String = loadConfig(s"google-analytics.host")
  override val betaFeedbackUrl = s"$contactHost/contact/beta-feedback?service=$contactFormServiceIdentifier"
  override val betaFeedbackUnauthenticatedUrl = s"$contactHost/contact/beta-feedback-unauthenticated?service=$contactFormServiceIdentifier"
  override val reportAProblemPartialUrl = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override val reportAProblemNonJSUrl = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  override val governmentGatewayUrl: String = loadConfig("government-gateway.url")
  override val blacklistedPostcodes: Set[String] =
    PostcodesLoader.load("/po_box_postcodes_abp_49.csv").map(x => x.toUpperCase.replace(" ", "")).toSet
  override val journeyName: String = getConfString("address-lookup-frontend.journeyName", "")
  override val agentServicesAccountUrl: String = s"$servicesAccountUrl$servicesAccountPath"
  override val domainWhiteList: Set[String] =
    runModeConfiguration.getStringList("continueUrl.domainWhiteList").getOrElse(emptyList()).toSet
  override val agentAssuranceFlag: Boolean = configuration.getBoolean("agentAssuranceFlag").getOrElse(false)
  override val addressLookupContinueUrl = getConfString("address-lookup-frontend.new-address-callback.url", "")
  override val surveyRedirectUrl: String = getConfString("surveyRedirectUrl", "")
  override val sosRedirectUrl: String = configuration.getString("sosRedirectUrl").getOrElse("")
  override val mongoDbKnownFactsResult: Int = configuration.getInt("mongodb.knownfactsresult.ttl").getOrElse(0)
  override val cacheableSessionDomain: String = getConfString("cachable.session-cache.domain", "")

}