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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import javax.inject.{Inject, Singleton}

import cats.data.NonEmptyList
import play.api.Logger
import play.api.data.Forms.{mapping, _}
import play.api.data.validation.ValidationError
import play.api.data.{Form, FormError}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json._
import play.api.mvc.{AnyContent, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{AgentRequest, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.{AddressLookupFrontendConnector, AuthenticatorConnector}
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.FieldMappings._
import uk.gov.hmrc.agentsubscriptionfrontend.form.DesAddressForm
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.support.CallOps
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.passcode.authentication.{PasscodeAuthenticationProvider, PasscodeVerificationConfig}
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.util.control.NonFatal

case class SubscriptionDetails(utr: Utr,
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
class SubscriptionController @Inject()
(override val messagesApi: MessagesApi,
 override val authConnector: AuthConnector,
 override val config: PasscodeVerificationConfig,
 override val passcodeAuthenticationProvider: PasscodeAuthenticationProvider,
 subscriptionService: SubscriptionService,
 sessionStoreService: SessionStoreService,
 addressLookUpValidator: AddressValidator,
 addressLookUpConnector: AddressLookupFrontendConnector,
 authenticatorConnector: AuthenticatorConnector
)
(implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport with AuthActions with SessionDataMissing {

  private val JourneyName: String = appConfig.journeyName
  private val blacklistedPostCodes: Set[String] = appConfig.blacklistedPostcodes

  val desAddressForm = new DesAddressForm(Logger, blacklistedPostCodes)

  private val initialDetailsForm = Form[InitialDetails](
    mapping(
      "utr" -> utr,
      "knownFactsPostcode" -> postcode,
      "name" -> agencyName,
      "email" -> email,
      "telephone" -> telephone
    )(InitialDetails.apply)(InitialDetails.unapply)
  )

  private case class SubscriptionReturnedHttpError(httpStatusCode: Int) extends Product with Serializable

  private def hasEnrolments(implicit request: AgentRequest[_]): Boolean = request.enrolments.nonEmpty

  val showInitialDetails: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() { implicit authContext =>
    implicit request =>
      hasEnrolments match {
        case true => Future(Redirect(routes.CheckAgencyController.showHasOtherEnrolments()))
        case false => sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
          Ok(html.subscription_details(knownFactsResult.taxpayerName, initialDetailsForm.fill(
            InitialDetails(knownFactsResult.utr, knownFactsResult.postcode, null, null, null))))
        }.getOrElse {
          sessionMissingRedirect()
        })
      }
  }

  val submitInitialDetails: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() {
    implicit authContext =>
      implicit request =>
        initialDetailsForm.bindFromRequest().fold(
          formWithErrors =>
            redisplayInitialDetails(formWithErrors),
          form =>
            addressLookUpConnector.initJourney(routes.SubscriptionController.returnFromAddressLookup(), JourneyName).map { x =>
              sessionStoreService.cacheInitialDetails(InitialDetails(form.utr, form.knownFactsPostcode, form.name,
                form.email, form.telephone))
              Redirect(x)
            }
        )
  }

  private def redisplayInitialDetails(formWithErrors: Form[InitialDetails])(implicit hc: HeaderCarrier, request: Request[_]) =
    sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
      Ok(html.subscription_details(knownFactsResult.taxpayerName, formWithErrors))
    }.getOrElse {
      sessionMissingRedirect()
    })

  import SubscriptionDetails._

  private def subscribe(details: InitialDetails,
                        address: DesAddress,
                        addressLookupAddress: Option[AddressLookupFrontendAddress])
                       (implicit hc: HeaderCarrier): Future[Either[SubscriptionReturnedHttpError, (Arn, String)]] = {
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
      case Right((arn, agencyName)) => Redirect(routes.SubscriptionController.showSubscriptionComplete())
        .flashing("arn" -> arn.arn, "agencyName" -> agencyName)
      case Left(SubscriptionReturnedHttpError(CONFLICT)) => Redirect(routes.CheckAgencyController.showAlreadySubscribed())
      case Left(SubscriptionReturnedHttpError(_)) => Redirect(routes.SubscriptionController.showSubscriptionFailed())
    }
  }

  def returnFromAddressLookup(id: String): Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() {
    implicit authContext =>
      implicit request =>

        sessionStoreService.fetchInitialDetails.flatMap { maybeDetails =>
          maybeDetails.map { details =>
            addressLookUpConnector.getAddressDetails(id).flatMap { address =>
              val addressForm = desAddressForm.bindAddressLookupFrontendAddress(details.utr, address)
              if (addressForm.hasErrors) {
                Future successful
                  Ok(html.address_form_with_errors(addressForm, SubscriptionController.renderDesAddressErrors(addressForm.errors)))
              } else {
                val subscriptResponse = for {
                  res ← subscribe(details, addressForm.get, Some(address))
                } yield res
                subscriptResponse.map(redirectSubscriptionResponse)
              }
            }
          }.getOrElse(Future.successful(sessionMissingRedirect()))
        }
  }

  def submitModifiedAddress: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() {
    implicit authContext =>
      implicit request =>
        desAddressForm.form.bindFromRequest().fold(
          formWithErrors => {
            Future successful Ok(html.address_form_with_errors(formWithErrors, ""))
          },
          validDesAddress =>
            sessionStoreService.fetchInitialDetails.flatMap { maybeInitialDetails =>
              maybeInitialDetails.map { initialDetails =>
                val subscriptResponse = for {
                  res ← subscribe(initialDetails, validDesAddress, None)
                  _ ← sessionStoreService.remove()
                } yield res
                subscriptResponse.map(redirectSubscriptionResponse)
              }.getOrElse(Future.successful(sessionMissingRedirect()))
            }
        )
  }

  val showSubscriptionFailed: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() {
    implicit authContext =>
      implicit request =>
        Future successful Ok(html.subscription_failed("Postcodes do not match"))
  }

  val showSubscriptionComplete: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() {
    implicit authContext =>
      implicit request => {
        authenticatorConnector.refreshEnrolments.flatMap { _ =>
          val agencyData = for {
            agencyName <- request.flash.get("agencyName")
            arn <- request.flash.get("arn")
          } yield (agencyName, arn)

          agencyData match {
            case Some((agencyName, arn)) =>
              sessionStoreService.fetchContinueUrl.
                recover { case NonFatal(ex) =>
                  Logger.warn("Session store service failure", ex)
                  None
                }.
                andThen { case _ => sessionStoreService.remove() }.
                map { continueUrlOpt =>
                  val continueUrl = CallOps.addParamsToUrl(appConfig.agentServicesAccountUrl, "continue" -> continueUrlOpt.map(_.url))
                  Ok(html.subscription_complete(continueUrl, agencyName, arn))
                }
            case _ =>
              Future.successful(sessionMissingRedirect())
          }
        }
      }
  }
}

object SubscriptionController {

  def renderErrors(errors: NonEmptyList[ValidationError])(implicit messages: Messages): String =
    errors
      .toList
      .map(valError => Messages(valError.message, valError.args: _*))
      .reduce(_ + ", " + _)

  def renderDesAddressErrors(errors: Seq[FormError])(implicit messages: Messages): String =
    errors
      .toList
      .map(valError => Messages(valError.message, valError.args: _*))
      .reduce(_ + ", " + _)
}
