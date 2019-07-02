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

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.mvc.{AnyContent, Request, _}
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AuditService
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.ValidationResult.FailureReason._
import uk.gov.hmrc.agentsubscriptionfrontend.models.ValidationResult.{Failure, Pass}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TaxIdentifierFormatters
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.validators.BusinessDetailsValidator
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BusinessIdentificationController @Inject()(
  assuranceService: AssuranceService,
  override val authConnector: AuthConnector,
  agentAssuranceConnector: AgentAssuranceConnector,
  val subscriptionService: SubscriptionService,
  val sessionStoreService: SessionStoreService,
  continueUrlActions: ContinueUrlActions,
  val businessDetailsValidator: BusinessDetailsValidator,
  auditService: AuditService)(
  implicit messagesApi: MessagesApi,
  override val appConfig: AppConfig,
  override val metrics: Metrics,
  override val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionBehaviour {

  import BusinessIdentificationForms._

  val showCreateNewAccount: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(html.create_new_account())
    }
  }

  val showNoMatchFound: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(html.no_match_found())
    }
  }

  val setupIncomplete: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(html.cannot_create_account())
    }
  }

  val showConfirmBusinessForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        getConfirmBusinessPage(existingSession)
      }
    }
  }

  private def getConfirmBusinessPage(existingSession: AgentSession, form: Form[ConfirmBusiness] = confirmBusinessForm)(
    implicit request: Request[_]) = {

    val getBackLinkForConfirmBusiness =
      routes.BusinessDetailsController.showBusinessDetailsForm()
//      existingSession.registeredForVat match {
//        case Some("Yes") => routes.VatDetailsController.showVatDetailsForm()
//        case _           => routes.VatDetailsController.showRegisteredForVatForm()
//      }

    (
      existingSession.utr,
      existingSession.registration.flatMap(_.taxpayerName),
      existingSession.registration.map(_.address)) match {
      case (Some(utr), Some(businessName), Some(address)) =>
        Ok(
          html.confirm_business(
            confirmBusinessRadioForm = form,
            registrationName = businessName,
            utr = TaxIdentifierFormatters.prettify(utr),
            businessAddress = address,
            getBackLinkForConfirmBusiness
          ))
      case (None, _, _) =>
        Logger.warn("utr is missing from registration, redirecting to /unique-taxpayer-reference")
        //Redirect(routes.UtrController.showUtrForm())
        Redirect(routes.BusinessDetailsController.showBusinessDetailsForm())
      case (_, None, _) =>
        Logger.warn("taxpayerName is missing from registration, redirecting to /business-name")
        Redirect(routes.BusinessIdentificationController.showBusinessNameForm())
      case (_, _, None) =>
        Logger.warn("business address is missing from registration, redirecting to /business-address")
        Redirect(routes.SubscriptionController.showBusinessAddressForm())
      case _ =>
        Redirect(routes.BusinessTypeController.showBusinessTypeForm())
    }
  }

  val submitConfirmBusinessForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        confirmBusinessForm
          .bindFromRequest()
          .fold(
            formWithErrors => getConfirmBusinessPage(existingSession, formWithErrors),
            validatedBusiness => {
              validatedBusiness.confirm match {
                case Yes =>
                  if (existingSession.registration.exists(_.isSubscribedToAgentServices)) {
                    mark("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
                    Redirect(routes.BusinessIdentificationController.showAlreadySubscribed())
                  } else {
                    sessionStoreService.fetchContinueUrl.flatMap { continueUrl =>
                      validatedBusinessDetailsAndRedirect(existingSession, continueUrl).map(Redirect)
                    }
                  }
                case No =>
                  //Redirect(routes.UtrController.showUtrForm())
                  Redirect(routes.BusinessDetailsController.showBusinessDetailsForm())
              }
            }
          )
      }
    }
  }

  private def validatedBusinessDetailsAndRedirect(existingSession: AgentSession, continueUrl: Option[ContinueUrl])(
    implicit hc: HeaderCarrier): Future[Call] =
    businessDetailsValidator.validate(existingSession.registration) match {
      case Failure(responses) if responses.contains(InvalidBusinessName) =>
        routes.BusinessIdentificationController.showBusinessNameForm()
      case Failure(responses) if responses.exists(r => r == InvalidBusinessAddress || r == DisallowedPostcode) =>
        routes.BusinessIdentificationController.showUpdateBusinessAddressForm()
      case Failure(responses) if responses.contains(InvalidEmail) =>
        routes.BusinessIdentificationController.showBusinessEmailForm()
      case _ if continueUrl.isDefined =>
        //if service is trusts don't show task list
        routes.AMLSController.showCheckAmlsPage()
      case _ =>
        for {
          isMAA <- agentAssuranceConnector.isManuallyAssuredAgent(existingSession.utr.get)
          _ <- if (isMAA)
                sessionStoreService.cacheAgentSession(existingSession.copy(taskListFlags = existingSession.taskListFlags
                  .copy(businessTaskComplete = true, amlsTaskComplete = true, createTaskComplete = true, isMAA = true)))
              else
                sessionStoreService.cacheAgentSession(
                  existingSession.copy(taskListFlags = existingSession.taskListFlags.copy(businessTaskComplete = true)))
          result <- routes.TaskListController.showTaskList()
        } yield result
    }

  val showBusinessEmailForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        Ok(
          html.business_email(
            existingSession.registration
              .flatMap(_.emailAddress)
              .fold(businessEmailForm)(email => businessEmailForm.fill(BusinessEmail(email))),
            hasInvalidEmail(existingSession.registration)
          ))
      }
    }
  }

  val changeBusinessEmail: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      sessionStoreService
        .cacheIsChangingAnswers(true)
        .map(_ => Redirect(routes.BusinessIdentificationController.showBusinessEmailForm().url))
    }
  }

  val submitBusinessEmailForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        businessEmailForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(html.business_email(formWithErrors, hasInvalidEmail(existingSession.registration))),
            validForm => {
              val updatedReg = existingSession.registration match {
                case Some(registration) => registration.copy(emailAddress = Some(validForm.email))
                case None =>
                  throw new IllegalStateException("expecting registration in the session, but not found") //TODO
              }

              updateSessionsAndRedirect(existingSession.copy(registration = Some(updatedReg)))
            }
          )
      }
    }
  }

  val showBusinessNameForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        Ok(
          html.business_name(
            businessNameForm.fill(BusinessName(existingSession.registration.flatMap(_.taxpayerName).getOrElse(""))),
            hasInvalidBusinessName(existingSession.registration)
          ))
      }
    }
  }

  val changeBusinessName: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      sessionStoreService
        .cacheIsChangingAnswers(true)
        .map(_ => Redirect(routes.BusinessIdentificationController.showBusinessNameForm().url))
    }
  }

  val submitBusinessNameForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        businessNameForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              Ok(html.business_name(formWithErrors, hasInvalidBusinessName(existingSession.registration))),
            validForm => {
              val updatedReg = existingSession.registration match {
                case Some(registration) => registration.copy(taxpayerName = Some(validForm.name))
                case None =>
                  throw new IllegalStateException("expecting registration in the session, but not found") //TODO
              }

              updateSessionsAndRedirect(existingSession.copy(registration = Some(updatedReg)))
            }
          )
      }
    }
  }

  private def updateSessionsAndRedirect(updatedSession: AgentSession)(implicit hc: HeaderCarrier) = {

    val result = for {
      _               <- sessionStoreService.cacheAgentSession(updatedSession)
      changingAnswers <- sessionStoreService.fetchIsChangingAnswers
    } yield changingAnswers

    result.flatMap[Result] {
      case Some(true) =>
        sessionStoreService
          .cacheIsChangingAnswers(false)
          .map(_ => Redirect(routes.SubscriptionController.showCheckAnswers()))
      case _ =>
        sessionStoreService.fetchContinueUrl.flatMap { continueUrl =>
          validatedBusinessDetailsAndRedirect(updatedSession, continueUrl).map(Redirect)
        }
    }
  }

  val showUpdateBusinessAddressForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        existingSession.registration match {
          case Some(registration) =>
            Ok(
              html.update_business_address(
                updateBusinessAddressForm.fill(models.UpdateBusinessAddressForm(registration.address))))
          case None => Redirect(routes.BusinessIdentificationController.showNoMatchFound())
        }
      }
    }
  }

  val submitUpdateBusinessAddressForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        updateBusinessAddressForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(html.update_business_address(formWithErrors)),
            validForm => {
              val updatedReg = existingSession.registration match {
                case Some(registration) =>
                  val updatedBusinessAddress = registration.address
                    .copy(
                      validForm.addressLine1,
                      validForm.addressLine2,
                      validForm.addressLine3,
                      validForm.addressLine4,
                      Some(validForm.postCode))
                  registration.copy(address = updatedBusinessAddress)
                case None =>
                  throw new IllegalStateException("expecting registration in the session, but not found") //TODO
              }

              businessDetailsValidator.validatePostcode(Some(validForm.postCode)) match {
                case Pass =>
                  updateSessionsAndRedirect(existingSession.copy(registration = Some(updatedReg)))
                case Failure(_) =>
                  Redirect(routes.BusinessIdentificationController.showPostcodeNotAllowed())
              }
            }
          )
      }
    }
  }

  val showPostcodeNotAllowed: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(html.postcode_not_allowed())
    }
  }

  val showAlreadySubscribed: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(html.already_subscribed())
    }
  }

  def hasInvalidBusinessName(registration: Option[Registration]): Boolean =
    businessDetailsValidator.validate(registration) match {
      case Failure(responses) if responses.contains(InvalidBusinessName) => true
      case _                                                             => false
    }

  def hasInvalidEmail(registration: Option[Registration]): Boolean =
    businessDetailsValidator.validate(registration) match {
      case Failure(responses) if responses.contains(InvalidEmail) => true
      case _                                                      => false
    }
}
