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

import javax.inject.{Inject, Singleton}
import java.util.Collections.emptyList
import play.api.{Configuration, Environment}
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
  val mongoDbKnownFactsResultTtl: Int
  val cacheableSessionDomain: String
}

@Singleton
class FrontendAppConfig @Inject()(val environment: Environment, val configuration: Configuration)
    extends AppConfig with ServicesConfig {

  override val runModeConfiguration: Configuration = configuration
  override protected def mode = environment.mode

  private val servicesAccountUrl = getServicesConfStringOrFail("agent-services-account-frontend.external-url")
  private val servicesAccountPath = getServicesConfStringOrFail("agent-services-account-frontend.start.path")

  override val analyticsToken: String = getConfStringOrFail(s"google-analytics.token")
  override val analyticsHost: String = getConfStringOrFail(s"google-analytics.host")

  override val betaFeedbackUrl: String = getConfStringOrFail("betaFeedbackUrl")
  override val betaFeedbackUnauthenticatedUrl: String = getConfStringOrFail("betaFeedbackUnauthenticatedUrl")
  override val reportAProblemPartialUrl: String = getConfStringOrFail("reportAProblemPartialUrl")
  override val reportAProblemNonJSUrl: String = getConfStringOrFail("reportAProblemNonJSUrl")

  override val governmentGatewayUrl: String = getConfStringOrFail("government-gateway.url")
  override val blacklistedPostcodes: Set[String] =
    PostcodesLoader.load("/po_box_postcodes_abp_49.csv").map(x => x.toUpperCase.replace(" ", "")).toSet
  override val journeyName: String = getServicesConfStringOrFail("address-lookup-frontend.journeyName")
  override val agentServicesAccountUrl: String = s"$servicesAccountUrl$servicesAccountPath"
  override val domainWhiteList: Set[String] =
    runModeConfiguration.getStringList("continueUrl.domainWhiteList").getOrElse(emptyList()).toSet
  override val agentAssuranceFlag: Boolean = getConfBooleanOrFail("agentAssuranceFlag")
  override val addressLookupContinueUrl: String = getServicesConfStringOrFail(
    "address-lookup-frontend.new-address-callback.url")
  override val surveyRedirectUrl: String = getConfStringOrFail(s"$env.surveyRedirectUrl")
  override val sosRedirectUrl: String = getConfStringOrFail(s"$env.sosRedirectUrl")
  override val mongoDbKnownFactsResultTtl: Int = getConfIntOrFail(s"$env.mongodb.knownfactsresult.ttl")
  override val cacheableSessionDomain: String = getServicesConfStringOrFail("cachable.session-cache.domain")

  def getServicesConfStringOrFail(key: String): String =
    getConfString(key, throw new Exception(s"Property not found $key"))
  def getConfStringOrFail(key: String): String =
    configuration.getString(key).getOrElse(throw new Exception(s"Property not found $key"))
  def getConfBooleanOrFail(key: String): Boolean =
    configuration.getBoolean(key).getOrElse(throw new Exception(s"Property not found $key"))
  def getConfIntOrFail(key: String): Int =
    configuration.getInt(key).getOrElse(throw new Exception(s"Property not found $key"))
}
