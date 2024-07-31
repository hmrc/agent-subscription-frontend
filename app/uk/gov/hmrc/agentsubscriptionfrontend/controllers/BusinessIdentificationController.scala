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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.api.{Configuration, Environment, Logging}
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AuditService
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{Agent, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.Llp
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.ValidationResult.FailureReason._
import uk.gov.hmrc.agentsubscriptionfrontend.models.ValidationResult.{Failure, Pass}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.service._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TaxIdentifierFormatters
import uk.gov.hmrc.agentsubscriptionfrontend.util._
import uk.gov.hmrc.agentsubscriptionfrontend.validators.BusinessDetailsValidator
import uk.gov.hmrc.agentsubscriptionfrontend.views.html._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BusinessIdentificationController @Inject() (
  val authConnector: AuthConnector,
  assuranceService: AssuranceService,
  val agentAssuranceConnector: AgentAssuranceConnector,
  val subscriptionService: SubscriptionService,
  val sessionStoreService: MongoDBSessionStoreService,
  val redirectUrlActions: RedirectUrlActions,
  val env: Environment,
  val config: Configuration,
  val metrics: Metrics,
  val subscriptionJourneyService: SubscriptionJourneyService,
  businessDetailsValidator: BusinessDetailsValidator,
  auditService: AuditService,
  mcc: MessagesControllerComponents,
  createNewAccountTemplate: create_new_account,
  noMatchFoundTemplate: no_match_found,
  companyNotAllowedTemplate: company_not_allowed,
  cannotCreateAccountTemplate: cannot_create_account,
  cannotConfirmIdentityTemplate: cannot_confirm_identity,
  confirmBusinessTemplate: confirm_business,
  businessEmailTemplate: business_email,
  existingJourneyFoundTemplate: existing_journey_found,
  businessNameTemplate: business_name,
  alreadySubscribedTemplate: already_subscribed,
  updateBusinessAddressTemplate: update_business_address,
  postcodeNotAllowedTemplate: postcode_not_allowed
)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with AuthActions with SessionBehaviour with I18nSupport with Logging {

  import BusinessIdentificationForms._

  def showCreateNewAccount: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(createNewAccountTemplate()).toFuture
    }
  }

  def showNoMatchFound: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(noMatchFoundTemplate()).toFuture
    }
  }

  def showCannotConfirmIdentity: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(cannotConfirmIdentityTemplate()).toFuture
    }
  }

  def showCompanyNotAllowed: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(companyNotAllowedTemplate()).toFuture
    }
  }

  def setupIncomplete: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(cannotCreateAccountTemplate()).toFuture
    }
  }

  def showConfirmBusinessForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        getConfirmBusinessPage(existingSession).toFuture
      }
    }
  }

  private def getConfirmBusinessPage(existingSession: AgentSession, form: Form[ConfirmBusiness] = confirmBusinessForm)(implicit
    request: Request[_]
  ) = {

    val getBackLinkForConfirmBusiness =
      if (existingSession.businessType.contains(Llp) && existingSession.isMAA.contains(true)) routes.PostcodeController.showPostcodeForm()
      else
        existingSession.registeredForVat match {
          case Some("Yes") => routes.VatDetailsController.showVatDetailsForm()
          case _           => routes.VatDetailsController.showRegisteredForVatForm()
        }

    (existingSession.utr, existingSession.registration.flatMap(_.taxpayerName), existingSession.registration.map(_.address)) match {
      case (Some(utr), Some(businessName), Some(address)) =>
        Ok(
          confirmBusinessTemplate(
            confirmBusinessRadioForm = form,
            registrationName = businessName,
            utr = TaxIdentifierFormatters.prettify(utr),
            businessAddress = address,
            getBackLinkForConfirmBusiness
          )
        )
      case (None, _, _) =>
        logger.warn("utr is missing from registration, redirecting to /unique-taxpayer-reference")
        Redirect(routes.UtrController.showUtrForm())
      case (_, None, _) =>
        logger.warn("taxpayerName is missing from registration, redirecting to /business-name")
        Redirect(routes.BusinessIdentificationController.showBusinessNameForm())
      case (_, _, None) =>
        logger.warn("business address is missing from registration, redirecting to /business-address")
        Redirect(routes.SubscriptionController.showBusinessAddressForm())
      case _ =>
        Redirect(routes.BusinessTypeController.showBusinessTypeForm())
    }
  }

  def submitConfirmBusinessForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        confirmBusinessForm
          .bindFromRequest()
          .fold(
            formWithErrors => getConfirmBusinessPage(existingSession, formWithErrors).toFuture,
            validatedBusiness =>
              validatedBusiness.confirm match {
                case Yes =>
                  if (existingSession.registration.exists(_.isSubscribedToAgentServices)) {
                    mark("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
                    Redirect(routes.BusinessIdentificationController.showAlreadySubscribed()).toFuture
                  } else validatedBusinessDetailsAndRedirect(existingSession, agent)
                case No =>
                  Redirect(routes.UtrController.showUtrForm())
              }
          )
      }
    }
  }

  private def validatedBusinessDetailsAndRedirect(existingSession: AgentSession, agent: Agent)(implicit
    request: Request[_],
    hc: HeaderCarrier
  ): Future[Result] =
    businessDetailsValidator.validate(existingSession.registration) match {
      case Failure(responses) if responses.contains(InvalidBusinessName) =>
        Redirect(routes.BusinessIdentificationController.showBusinessNameForm())
      case Failure(responses) if responses.exists(r => r == InvalidBusinessAddress || r == DisallowedPostcode) =>
        Redirect(routes.BusinessIdentificationController.showUpdateBusinessAddressForm())
      case Failure(responses) if responses.contains(InvalidEmail) =>
        Redirect(routes.BusinessIdentificationController.showBusinessEmailForm())
      case _ =>
        subscriptionService.handlePartiallySubscribedAndRedirect(agent, existingSession)(
          whenNotPartiallySubscribed = createRecordAndRedirectToTasklist(existingSession, agent)
        )
    }

  private def createRecordAndRedirectToTasklist(existingSession: AgentSession, agent: Agent)(implicit hc: HeaderCarrier): Future[Result] =
    subscriptionJourneyService
      .createJourneyRecord(existingSession, agent)
      .map(_ => Redirect(routes.TaskListController.showTaskList()))

  def showBusinessEmailForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        Ok(
          businessEmailTemplate(
            existingSession.registration
              .flatMap(_.emailAddress)
              .fold(businessEmailForm)(email => businessEmailForm.fill(BusinessEmail(email))),
            hasInvalidEmail(existingSession.registration),
            isChange = false
          )
        )
      }
    }
  }

  def changeBusinessEmail: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      val sjr = agent.getMandatorySubscriptionRecord
      Ok(
        businessEmailTemplate(
          sjr.businessDetails.registration
            .flatMap(_.emailAddress)
            .fold(businessEmailForm)(email => businessEmailForm.fill(BusinessEmail(email))),
          hasInvalidEmail(sjr.businessDetails.registration),
          isChange = true
        )
      )
    }
  }

  def submitChangeBusinessEmail: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      val currentSjr = agent.getMandatorySubscriptionRecord
      businessEmailForm
        .bindFromRequest()
        .fold(
          formWithErrors => Ok(businessEmailTemplate(formWithErrors, hasInvalidEmail(currentSjr.businessDetails.registration), isChange = true)),
          validForm => {

            val updatedSjr: SubscriptionJourneyRecord = currentSjr.copy(
              businessDetails = currentSjr.businessDetails.copy(
                registration = Some(
                  currentSjr.businessDetails.registration
                    .getOrElse(throw new RuntimeException("missing registration data"))
                    .copy(emailAddress = Some(validForm.email))
                )
              )
            )
            for {
              _ <- subscriptionJourneyService.saveJourneyRecord(updatedSjr)
              goto <-
                Redirect(
                  continueOrStop(routes.SubscriptionController.showCheckAnswers(), routes.BusinessIdentificationController.changeBusinessEmail())
                )
            } yield goto
          }
        )
    }
  }

  def submitBusinessEmailForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        businessEmailForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(businessEmailTemplate(formWithErrors, hasInvalidEmail(existingSession.registration), isChange = false)),
            validForm => {
              val updatedReg = existingSession.registration match {
                case Some(registration) => registration.copy(emailAddress = Some(validForm.email))
                case None =>
                  throw new IllegalStateException("expecting registration in the session, but not found") // TODO
              }

              updateSessionsAndRedirect(existingSession.copy(registration = Some(updatedReg)), agent)
            }
          )
      }
    }
  }

  def showBusinessNameForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        Ok(
          businessNameTemplate(
            businessNameForm.fill(BusinessName(existingSession.registration.flatMap(_.taxpayerName).getOrElse(""))),
            hasInvalidBusinessName(existingSession.registration),
            isChange = false
          )
        )
      }
    }
  }

  def changeBusinessName: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      val sjr = agent.getMandatorySubscriptionRecord
      Ok(
        businessNameTemplate(
          businessNameForm.fill(BusinessName(sjr.businessDetails.registration.flatMap(_.taxpayerName).getOrElse(""))),
          hasInvalidBusinessName(sjr.businessDetails.registration),
          isChange = true
        )
      )
    }
  }

  def submitChangeBusinessName: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      val currentSjr = agent.getMandatorySubscriptionRecord
      businessNameForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Ok(businessNameTemplate(formWithErrors, hasInvalidBusinessName(currentSjr.businessDetails.registration), isChange = true)),
          validForm => {
            val updatedSjr = currentSjr
              .copy(businessDetails =
                currentSjr.businessDetails
                  .copy(registration =
                    Some(
                      currentSjr.businessDetails.registration
                        .getOrElse(throw new RuntimeException("missing registration data"))
                        .copy(taxpayerName = Some(validForm.name))
                    )
                  )
              )

            for {
              _ <- subscriptionJourneyService.saveJourneyRecord(updatedSjr)
              goto <- Redirect(
                        continueOrStop(routes.SubscriptionController.showCheckAnswers(), routes.BusinessIdentificationController.changeBusinessName())
                      )
            } yield goto
          }
        )
    }
  }

  def submitBusinessNameForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        businessNameForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(businessNameTemplate(formWithErrors, hasInvalidBusinessName(existingSession.registration), isChange = false)),
            validForm => {
              val updatedReg = existingSession.registration match {
                case Some(registration) => registration.copy(taxpayerName = Some(validForm.name))
                case None =>
                  throw new IllegalStateException("expecting registration in the session, but not found") // TODO
              }

              updateSessionsAndRedirect(existingSession.copy(registration = Some(updatedReg)), agent)
            }
          )
      }
    }
  }

  private def updateSessionsAndRedirect(updatedSession: AgentSession, agent: Agent)(implicit request: Request[_], hc: HeaderCarrier) = {

    val result = for {
      _               <- sessionStoreService.cacheAgentSession(updatedSession)
      changingAnswers <- sessionStoreService.fetchIsChangingAnswers
    } yield changingAnswers

    result.flatMap[Result] {
      case Some(true) =>
        sessionStoreService
          .cacheIsChangingAnswers(changing = false)
          .map(_ => Redirect(routes.SubscriptionController.showCheckAnswers()))

      case _ => validatedBusinessDetailsAndRedirect(updatedSession, agent)
    }
  }

  def showUpdateBusinessAddressForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        existingSession.registration match {
          case Some(registration) =>
            Ok(updateBusinessAddressTemplate(updateBusinessAddressForm.fill(models.UpdateBusinessAddressForm(registration.address))))
          case None => Redirect(routes.BusinessIdentificationController.showNoMatchFound())
        }
      }
    }
  }

  def submitUpdateBusinessAddressForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        updateBusinessAddressForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(updateBusinessAddressTemplate(formWithErrors)),
            validForm => {
              val updatedReg = existingSession.registration match {
                case Some(registration) =>
                  val updatedBusinessAddress = registration.address
                    .copy(validForm.addressLine1, validForm.addressLine2, validForm.addressLine3, validForm.addressLine4, Some(validForm.postCode))
                  registration.copy(address = updatedBusinessAddress)
                case None =>
                  throw new IllegalStateException("expecting registration in the session, but not found") // TODO
              }

              businessDetailsValidator.validatePostcode(Some(validForm.postCode)) match {
                case Pass =>
                  updateSessionsAndRedirect(existingSession.copy(registration = Some(updatedReg)), agent)
                case Failure(_) =>
                  Redirect(routes.BusinessIdentificationController.showPostcodeNotAllowed())
              }
            }
          )
      }
    }
  }

  def showPostcodeNotAllowed: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(postcodeNotAllowedTemplate())
    }
  }

  def showAlreadySubscribed: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      for {
        agentSubContinueUrlOpt <- sessionStoreService.fetchContinueUrl
        redirectUrl            <- redirectUrlActions.getUrl(agentSubContinueUrlOpt)
      } yield {
        val continueUrl = redirectUrl.getOrElse(routes.SignedOutController.redirectToBusinessTypeForm().url)
        Ok(alreadySubscribedTemplate(continueUrl))
      }
    }
  }

  def showExistingJourneyFound: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(existingJourneyFoundTemplate())
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

object BusinessIdentificationController {
  val dateFormatter = DateTimeFormatter.ofPattern("dd MM yyyy")
}
