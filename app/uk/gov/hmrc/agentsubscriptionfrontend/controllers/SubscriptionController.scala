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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import com.kenshoo.play.metrics.Metrics
import javax.inject.{ Inject, Singleton }
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json._
import play.api.mvc.{ AnyContent, _ }
import play.api.{ Configuration, Logger }
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent._
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AddressLookupFrontendConnector
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.FieldMappings._
import uk.gov.hmrc.agentsubscriptionfrontend.form.DesAddressForm
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{ SessionStoreService, SubscriptionService }
import uk.gov.hmrc.agentsubscriptionfrontend.support.{ CallOps, Monitoring }
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future
import scala.util.control.NonFatal

case class SubscriptionDetails(
  utr: Utr,
  knownFactsPostcode: String,
  name: String,
  email: String,
  telephone: String,
  address: DesAddress)

object SubscriptionDetails {
  implicit val formatDesAddress: Format[DesAddress] = Json.format[DesAddress]
  implicit val formatSubscriptionDetails: Format[SubscriptionDetails] = Json.format[SubscriptionDetails]

  implicit def mapper(initDetails: InitialDetails, address: DesAddress): SubscriptionDetails = {
    SubscriptionDetails(initDetails.utr, initDetails.knownFactsPostcode, initDetails.name,
      initDetails.email, initDetails.telephone, address)
  }
}

@Singleton
class SubscriptionController @Inject() (
  override val messagesApi: MessagesApi,
  override val authConnector: AuthConnector,
  subscriptionService: SubscriptionService,
  sessionStoreService: SessionStoreService,
  addressLookUpConnector: AddressLookupFrontendConnector,
  val continueUrlActions: ContinueUrlActions,
  val metrics: Metrics,
  override val appConfig: AppConfig)
  extends FrontendController with I18nSupport with AuthActions with SessionDataMissing with Monitoring {

  implicit val configuration: Configuration = appConfig.configuration

  val desAddressForm = new DesAddressForm(Logger, appConfig.blacklistedPostcodes)

  private val initialDetailsForm = Form[InitialDetails](
    mapping(
      "utr" -> utr,
      "knownFactsPostcode" -> postcode,
      "name" -> agencyName,
      "email" -> emailAddress,
      "telephone" -> telephone)(InitialDetails.apply)(InitialDetails.unapply))

  private case class SubscriptionReturnedHttpError(httpStatusCode: Int) extends Product with Serializable

  val showInitialDetails: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent {
      case hasNonEmptyEnrolments(_) => Future(Redirect(routes.CheckAgencyController.showHasOtherEnrolments()))
      case _ =>
        mark("Count-Subscription-CleanCreds-Success")
        sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
          Ok(html.subscription_details(knownFactsResult.taxpayerName, initialDetailsForm.fill(
            InitialDetails(knownFactsResult.utr, knownFactsResult.postcode, null, null, null))))
        }.getOrElse {
          sessionMissingRedirect()
        })
    }
  }

  val submitInitialDetails: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      initialDetailsForm.bindFromRequest().fold(
        formWithErrors =>
          redisplayInitialDetails(formWithErrors),
        form => {
          mark("Count-Subscription-AddressLookup-Start")
          addressLookUpConnector.initJourney(routes.SubscriptionController.returnFromAddressLookup(), appConfig.journeyName).map { x =>
            sessionStoreService.cacheInitialDetails(InitialDetails(form.utr, form.knownFactsPostcode, form.name,
              form.email, form.telephone))
            Redirect(x)
          }
        })
    }
  }

  private def redisplayInitialDetails(formWithErrors: Form[InitialDetails])(implicit hc: HeaderCarrier, request: Request[_]) =
    sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
      Ok(html.subscription_details(knownFactsResult.taxpayerName, formWithErrors))
    }.getOrElse {
      sessionMissingRedirect()
    })

  import SubscriptionDetails._

  private def subscribe(
    details: InitialDetails,
    address: DesAddress)(implicit hc: HeaderCarrier): Future[Either[SubscriptionReturnedHttpError, (Arn, String)]] = {
    val subscriptionDetails = mapper(details, address)
    subscriptionService.subscribeAgencyToMtd(subscriptionDetails) map {
      case Right(arn) => {
        Right((arn, subscriptionDetails.name))
      }
      case Left(x) => Left(SubscriptionReturnedHttpError(x))
    }
  }

  private def redirectSubscriptionResponse(either: Either[SubscriptionReturnedHttpError, (Arn, String)]): Result = {
    either match {
      case Right((arn, agencyName)) =>
        mark("Count-Subscription-Complete")
        Redirect(routes.SubscriptionController.showSubscriptionComplete())
          .flashing("arn" -> arn.arn, "agencyName" -> agencyName)

      case Left(SubscriptionReturnedHttpError(CONFLICT)) =>
        mark("Count-Subscription-AlreadySubscribed-APIResponse")
        Redirect(routes.CheckAgencyController.showAlreadySubscribed())

      case Left(SubscriptionReturnedHttpError(_)) =>
        mark("Count-Subscription-Failed")
        Redirect(routes.SubscriptionController.showSubscriptionFailed())
    }
  }

  def returnFromAddressLookup(id: String): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      sessionStoreService.fetchInitialDetails.flatMap { maybeDetails =>
        maybeDetails.map { details =>
          addressLookUpConnector.getAddressDetails(id).flatMap { address =>
            desAddressForm.bindAddressLookupFrontendAddress(details.utr, address).fold(
              formWithErrors => Future successful Ok(html.address_form_with_errors(formWithErrors)),
              validDesAddress => {
                mark("Count-Subscription-AddressLookup-Success")
                subscribe(details, validDesAddress).map(redirectSubscriptionResponse)
              })
          }
        }.getOrElse(Future.successful(sessionMissingRedirect()))
      }
    }
  }

  def submitModifiedAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      desAddressForm.form.bindFromRequest().fold(
        formWithErrors => Future successful Ok(html.address_form_with_errors(formWithErrors)),
        validDesAddress =>
          sessionStoreService.fetchInitialDetails.flatMap { maybeInitialDetails =>
            maybeInitialDetails.map { initialDetails =>
              subscribe(initialDetails, validDesAddress).map(redirectSubscriptionResponse)
            }.getOrElse(Future.successful(sessionMissingRedirect()))
          })
    }
  }

  val showSubscriptionFailed: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future successful Ok(html.subscription_failed("Postcodes do not match"))
    }
  }

  val showSubscriptionComplete: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      {
        val agencyData = for {
          agencyName <- request.flash.get("agencyName")
          arn <- request.flash.get("arn")
        } yield (agencyName, arn)

        agencyData match {
          case Some((agencyName, arn)) =>
            sessionStoreService.fetchContinueUrl.
              recover {
                case NonFatal(ex) =>
                  Logger.warn("Session store service failure", ex)
                  None
              }.
              andThen { case _ => sessionStoreService.remove() }.
              map { continueUrlOpt =>
                val continueUrl = CallOps.addParamsToUrl(configuration.getString("agent-services-account-frontend").get, "continue" -> continueUrlOpt.map(_.url))
                Ok(html.subscription_complete(continueUrl, agencyName, arn))
              }
          case _ =>
            Future.successful(sessionMissingRedirect())
        }
      }
    }
  }
}
