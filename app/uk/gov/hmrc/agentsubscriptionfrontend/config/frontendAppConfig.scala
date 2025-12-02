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

package uk.gov.hmrc.agentsubscriptionfrontend.config

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.i18n.Lang
import play.api.mvc.Call
import uk.gov.hmrc.agentsubscriptionfrontend.config.denylistedPostcodes.PostcodesLoader
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.routes
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@ImplementedBy(classOf[FrontendAppConfig])
trait AppConfig {
  val governmentGatewayUrl: String
  val denylistedPostcodes: Set[String]
  val agentServicesAccountUrl: String
  val agentAssuranceBaseUrl: String
  val agentAssuranceRun: Boolean
  val surveyRedirectUrl: String
  val chainedSessionDetailsTtl: Int
  val addressLookupFrontendBaseUrl: String
  val ggRegistrationFrontendExternalUrl: String
  val ssoBaseUrl: String
  val returnAfterGGCredsCreatedUrl: String
  val agentSubscriptionBaseUrl: String
  val selfExternalUrl: String
  val timeout: Int
  val timeoutCountdown: Int
  val appName: String
  val languageToggle: Boolean
  val languageMap: Map[String, Lang]
  def routeToSwitchLanguage: String => Call
  val companiesHouseUrl: String
  val mongoDbExpireAfterSeconds: Int
  val loginContinueUrl: String
  val amlsGuidanceLink: String
  val emailVerificationBaseUrl: String
  val emailVerificationFrontendBaseUrl: String
  val disableEmailVerification: Boolean
  val signOutUrl: String
  val signInUrl: String
}

@Singleton
class FrontendAppConfig @Inject() (servicesConfig: ServicesConfig) extends AppConfig {

  override val appName = "agent-subscription-frontend"

  private def getConf(key: String): String = servicesConfig.getString(key)

  private val servicesAccountUrl = getConf("microservice.services.agent-services-account-frontend.external-url")
  private val servicesAccountPath =
    getConf("microservice.services.agent-services-account-frontend.start.path")

  override val governmentGatewayUrl: String = getConf("government-gateway.url")
  override val denylistedPostcodes: Set[String] =
    PostcodesLoader.load("/po_box_postcodes_abp_49.csv").map(x => x.toUpperCase.replace(" ", "")).toSet
  override val agentServicesAccountUrl: String = s"$servicesAccountUrl$servicesAccountPath"
  override lazy val agentAssuranceBaseUrl: String = servicesConfig.baseUrl("agent-assurance")
  override val agentAssuranceRun: Boolean = servicesConfig.getBoolean("features.agent-assurance-run")
  override val surveyRedirectUrl: String = getConf("surveyRedirectUrl")
  override val selfExternalUrl: String = getConf("microservice.services.agent-subscription-frontend.external-url")

  override val chainedSessionDetailsTtl: Int = servicesConfig.getInt("mongodb.chainedsessiondetails.ttl")

  override val addressLookupFrontendBaseUrl: String = servicesConfig.baseUrl("address-lookup-frontend")

  val ssoRedirectUrl: String = "/government-gateway-registration-frontend"

  override val agentSubscriptionBaseUrl: String = servicesConfig.baseUrl("agent-subscription")

  override val ssoBaseUrl: String = servicesConfig.baseUrl("sso")

  override val ggRegistrationFrontendExternalUrl: String =
    s"${getConf("microservice.services.government-gateway-registration-frontend.externalUrl")}$ssoRedirectUrl"

  private val returnAfterGGCredsCreatedPath: String = "/agent-subscription/return-after-gg-creds-created"
  override val returnAfterGGCredsCreatedUrl: String = s"$selfExternalUrl$returnAfterGGCredsCreatedPath"

  override val timeout: Int = servicesConfig.getInt("timeoutDialog.timeout-seconds")
  override val timeoutCountdown: Int = servicesConfig.getInt("timeoutDialog.timeout-countdown-seconds")

  override val languageToggle: Boolean = servicesConfig.getBoolean("features.enable-welsh-toggle")

  override val languageMap: Map[String, Lang] = Map(
    "english" -> Lang("en"),
    "cymraeg" -> Lang("cy")
  )

  override def routeToSwitchLanguage: String => Call =
    (lang: String) => routes.AgentSubscriptionLanguageController.switchToLanguage(lang)

  override val companiesHouseUrl: String = getConf("companies-house.url")
  override val mongoDbExpireAfterSeconds: Int = servicesConfig.getInt("mongodb.session.expireAfterSeconds")
  override val loginContinueUrl: String = servicesConfig.getString("login.continue")

  override val amlsGuidanceLink: String = "https://www.gov.uk/guidance/money-laundering-regulations-appeals-and-penalties"
  override val emailVerificationBaseUrl: String = servicesConfig.baseUrl("email-verification")
  override val emailVerificationFrontendBaseUrl: String =
    servicesConfig.getString("microservice.services.email-verification-frontend.external-url")
  override val disableEmailVerification: Boolean = servicesConfig.getBoolean("disable-email-verification")

  private val basGatewayFrontendExternalUrl: String = servicesConfig.getString("bas-gateway-frontend.external-url")
  private val signOutPath: String = servicesConfig.getString("bas-gateway-frontend.sign-out.path")
  private val signInPath: String = servicesConfig.getString("bas-gateway-frontend.sign-in.path")
  override lazy val signOutUrl: String = s"$basGatewayFrontendExternalUrl$signOutPath"
  override lazy val signInUrl: String = s"$basGatewayFrontendExternalUrl$signInPath"
}
