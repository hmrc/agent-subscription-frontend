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

import javax.inject.{Inject, Singleton}

import com.kenshoo.play.metrics.Metrics
import play.api.Logger
import play.api.data.{Form, FormError}
import play.api.data.Forms.mapping
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AnyContent, Request, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AuditService
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{Agent, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.{AgentAssuranceConnector, AgentSubscriptionConnector}
import uk.gov.hmrc.agentsubscriptionfrontend.models.AssuranceResults._
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service._
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.{invasive_check_start, invasive_input_option}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.{Nino, SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, InternalServerException}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import play.api.data.Forms._
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent.hasNonEmptyEnrolments

import scala.concurrent.Future

object CheckAgencyController {
  val validBusinessTypes = Seq("sole_trader", "limited_company", "partnership", "llp")

  val knownFactsForm: Form[KnownFacts] =
    Form[KnownFacts](
      mapping("utr" -> FieldMappings.utr, "postcode" -> FieldMappings.postcode)(
        (utrStr, postcode) =>
          FieldMappings
            .normalizeUtr(utrStr)
            .map(utr => KnownFacts(utr, postcode))
            .getOrElse(throw new Exception("Invalid utr found after validation")))(knownFacts =>
        Some((knownFacts.utr.value, knownFacts.postcode))))

  val businessTypeForm: Form[BusinessType] =
    Form[BusinessType](
      mapping("businessType" -> optional(text).verifying(FieldMappings.radioInputSelected))(BusinessType.apply)(
        BusinessType.unapply)
        .verifying(
          "error.business-type-value.invalid",
          submittedBusinessType => validBusinessTypes.contains(submittedBusinessType.businessType.getOrElse(""))))
}

@Singleton
class CheckAgencyController @Inject()(
  assuranceService: AssuranceService,
  val agentAssuranceConnector: AgentAssuranceConnector,
  override val messagesApi: MessagesApi,
  override val authConnector: AuthConnector,
  val agentSubscriptionConnector: AgentSubscriptionConnector,
  val subscriptionService: SubscriptionService,
  val sessionStoreService: SessionStoreService,
  val continueUrlActions: ContinueUrlActions,
  auditService: AuditService,
  override val appConfig: AppConfig,
  val metrics: Metrics)(implicit val aConfig: AppConfig)
    extends FrontendController with I18nSupport with AuthActions with SessionDataMissing with Monitoring {

  import continueUrlActions._

  val showHasOtherEnrolments: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future successful Ok(html.has_other_enrolments())
    }
  }

  def showCheckBusinessType: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withMaybeContinueUrlCached {
        Future successful Ok(html.check_business_type(CheckAgencyController.businessTypeForm))
      }
    }
  }

  def submitCheckBusinessType: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      CheckAgencyController.businessTypeForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            if (formWithErrors.errors.exists(_.message == "error.business-type-value.invalid")) {
              Logger.warn("Select business-type form submitted with invalid identifier")
              throw new BadRequestException("submitted form value did not contain valid businessType identifier")
            }
            Future successful Ok(html.check_business_type(formWithErrors))
          },
          validatedBusinessType =>
            Future successful Redirect(
              routes.CheckAgencyController.showCheckAgencyStatus(validatedBusinessType.businessType))
        )
    }
  }

  def showCheckAgencyStatus(businessType: Option[String]): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      //withMaybeContinueUrlCached because, Currently still needed as a user might be arriving from: trusts registration flow or gov.uk guidence page, make sure this is not the case anymore before removing
      withMaybeContinueUrlCached {
        businessType match {
          case Some(businessTypeIdentifier)
              if CheckAgencyController.validBusinessTypes.contains(businessTypeIdentifier) => {
            mark("Count-Subscription-CheckAgency-Start")
            Future successful Ok(html.check_agency_status(CheckAgencyController.knownFactsForm, businessTypeIdentifier))
          }
          case _ => {
            Logger.warn("businessTypeIdentifier was missing, redirect and obtain from showCheckBusinessType page")
            Future successful Redirect(routes.CheckAgencyController.showCheckBusinessType())
          }
        }
      }
    }
  }

  def checkAgencyStatus(businessType: Option[String]): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      businessType match {
        case Some(businessTypeIdentifier)
            if CheckAgencyController.validBusinessTypes.contains(businessTypeIdentifier) => {
          CheckAgencyController.knownFactsForm
            .bindFromRequest()
            .fold(
              formWithErrors => Future successful Ok(html.check_agency_status(formWithErrors, businessTypeIdentifier)),
              knownFacts => checkAgencyStatusGivenValidForm(knownFacts)
            )
        }
        case _ => Future successful Redirect(routes.CheckAgencyController.showCheckBusinessType())
      }
    }
  }

  private def checkAgencyStatusGivenValidForm(
    knownFacts: KnownFacts)(implicit hc: HeaderCarrier, request: Request[AnyContent], agent: Agent): Future[Result] = {

    def cacheKnownFactsAndAudit(
      maybeAssuranceResults: Option[AssuranceResults],
      taxpayerName: String,
      isSubscribedToAgentServices: Boolean) = {
      val knownFactsResult =
        KnownFactsResult(knownFacts.utr, knownFacts.postcode, taxpayerName, isSubscribedToAgentServices)
      for {
        _ <- sessionStoreService.cacheKnownFactsResult(knownFactsResult)
        _ <- maybeAssuranceResults
              .map(auditService.sendAgentAssuranceAuditEvent(knownFactsResult, _))
              .getOrElse(Future.successful(()))
      } yield ()
    }

    def processCheckAgencyStatus(utr: Utr, taxpayerName: String, isSubscribedToAgentServices: Boolean): Future[Result] =
      assuranceService.assureIsAgent(knownFacts.utr).flatMap {
        case RefuseToDealWith(_) =>
          Future.successful(Redirect(routes.StartController.setupIncomplete()))
        case CheckedInvisibleAssuranceAndFailed(assuranceResults) =>
          cacheKnownFactsAndAudit(Some(assuranceResults), taxpayerName, isSubscribedToAgentServices).map { _ =>
            mark("Count-Subscription-InvasiveCheck-Start")
            Redirect(routes.CheckAgencyController.invasiveCheckStart())
          }
        case maybeAssured @ (None | ManuallyAssured(_) | CheckedInvisibleAssuranceAndPassed(_)) => {
          cacheKnownFactsAndAudit(maybeAssured, taxpayerName, isSubscribedToAgentServices).map { _ =>
            mark("Count-Subscription-CheckAgency-Success")
            Redirect(routes.CheckAgencyController.showConfirmYourAgency())
          }
        }
      }

    subscriptionService.getSubscriptionStatus(knownFacts.utr, knownFacts.postcode).flatMap {
      case SubscriptionProcess(SubscriptionState.BrandNewSubscription, Some(registrationDetails)) =>
        processCheckAgencyStatus(
          knownFacts.utr,
          registrationDetails.taxpayerName.get,
          registrationDetails.isSubscribedToAgentServices)
      case SubscriptionProcess(SubscriptionState.IsOnlySubscribedInETMP, Some(registrationDetails)) =>
        agent match {
          case hasNonEmptyEnrolments(_) =>
            Future successful Redirect(routes.CheckAgencyController.showHasOtherEnrolments())
          case _ => {
            subscriptionService
              .completePartialSubscription(
                CompletePartialSubscriptionBody(knownFacts.utr, SubscriptionRequestKnownFacts(knownFacts.postcode)))
              .map {
                case (_, Some(arn)) =>
                  Redirect(routes.SubscriptionController.showSubscriptionComplete()).flashing("arn" -> arn.value)
                case _ => throw new InternalServerException("partialSubscription fix executed, but failed to complete")
              }
          }
        }
      case SubscriptionProcess(SubscriptionState.IsSubscribedToAgentServices, _) => {
        mark("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
        Future successful Redirect(routes.CheckAgencyController.showAlreadySubscribed())
      }
      case _ => {
        mark("Count-Subscription-NoAgencyFound")
        Future successful Redirect(routes.CheckAgencyController.showNoAgencyFound())
      }
    }
  }

  val showNoAgencyFound: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future successful Ok(html.no_agency_found())
    }
  }

  val setupIncomplete: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future successful Ok(html.setup_incomplete())
    }
  }

  val showConfirmYourAgency: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
        Ok(
          html.confirm_your_agency(
            registrationName = knownFactsResult.taxpayerName,
            postcode = knownFactsResult.postcode,
            utr = FieldMappings.prettify(knownFactsResult.utr),
            nextPageUrl = lookupNextPageUrl(knownFactsResult.isSubscribedToAgentServices)
          ))
      }.getOrElse {
        sessionMissingRedirect()
      })
    }
  }

  private def lookupNextPageUrl(isSubscribedToAgentServices: Boolean): String =
    if (isSubscribedToAgentServices) {
      mark("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
      routes.CheckAgencyController.showAlreadySubscribed().url
    } else {
      mark("Count-Subscription-CleanCreds-Start")
      routes.SubscriptionController.showInitialDetails().url
    }

  val showAlreadySubscribed: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future successful Ok(html.already_subscribed())
    }
  }

  def invasiveCheckStart: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future.successful(Ok(invasive_check_start(RadioWithInput.confirmResponseForm)))
    }
  }

  def invasiveSaAgentCodePost: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      RadioWithInput.confirmResponseForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            Future.successful(Ok(invasive_check_start(formWithErrors)))
          },
          correctForm => {
            if (correctForm.value.getOrElse(false)) {
              val saAgentReference = correctForm.messageOfTrueRadioChoice.getOrElse("")

              val validationResult = FieldMappings.saAgentCode
                .withPrefix("confirmResponse-true-hidden-input")
                .bind(Map("confirmResponse-true-hidden-input" -> saAgentReference.replace(" ", "")))

              validationResult match {
                case Right(code) =>
                  Future.successful(Redirect(routes.CheckAgencyController.invasiveTaxPayerOptionGet())
                    .withSession(request.session + ("saAgentReferenceToCheck" -> code)))
                case Left(formErrors) =>
                  formErrors.headOption
                    .map(error =>
                      Future.successful(Ok(invasive_check_start(RadioWithInput.confirmResponseForm.withError(error)))))
                    .getOrElse(
                      throw new InternalServerException("SaAgentCode form validation returned empty errors object"))
              }
            } else {
              mark("Count-Subscription-InvasiveCheck-Declined")
              Future.successful(Redirect(routes.StartController.setupIncomplete()))
            }
          }
        )
    }
  }

  def invasiveTaxPayerOptionGet: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future.successful(Ok(invasive_input_option(RadioWithInput.confirmResponseForm)))
    }
  }

  def invasiveTaxPayerOption: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      RadioWithInput.confirmResponseForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            Future.successful(Ok(invasive_input_option(formWithErrors)))
          },
          correctForm => {
            val trueIsNinoFalseIsUtr = correctForm.value.getOrElse(false)

            val validatedIdentifier = if (trueIsNinoFalseIsUtr) {
              val id = correctForm.messageOfTrueRadioChoice.getOrElse("")
              Nino.isValid(id) match {
                case true  => Right(id)
                case false => Left(Seq(FormError("confirmResponse-true-hidden-input", "error.nino.invalid")))
              }
            } else {
              val utrStr = correctForm.messageOfFalseRadioChoice.getOrElse("")
              FieldMappings.utr
                .withPrefix("confirmResponse-false-hidden-input")
                .bind(Map("confirmResponse-false-hidden-input" -> utrStr))
            }

            validatedIdentifier match {
              case Left(seqErrors) =>
                Future.successful(
                  Ok(invasive_input_option(RadioWithInput.confirmResponseForm.withError(seqErrors.headOption
                    .getOrElse(throw new InternalServerException("could not provide the user with errors found"))))))
              case Right(identifier) => {
                val (taxIdentifier, taxIdentifierName) =
                  if (trueIsNinoFalseIsUtr) (Nino(identifier), "nino")
                  else
                    (
                      FieldMappings
                        .normalizeUtr(identifier)
                        .getOrElse(
                          throw new InternalServerException("Utr passed validation but failed the normalize method")),
                      "utr")
                checkAndRedirect(taxIdentifier, taxIdentifierName)
              }
            }
          }
        )
    }
  }

  private def checkAndRedirect(
    value: TaxIdentifier,
    taxIdentifierName: String)(implicit hc: HeaderCarrier, request: Request[AnyContent], agent: Agent) =
    request.session.get("saAgentReferenceToCheck") match {
      case Some(saAgentReference) =>
        assuranceService
          .checkActiveCesaRelationship(value, taxIdentifierName, SaAgentReference(saAgentReference))
          .map {
            case true =>
              mark("Count-Subscription-InvasiveCheck-Success")
              Redirect(routes.CheckAgencyController.showConfirmYourAgency())
            case false =>
              mark("Count-Subscription-InvasiveCheck-Failed")
              Redirect(routes.StartController.setupIncomplete())
          }
      case None => Future.successful(Redirect(routes.CheckAgencyController.invasiveCheckStart()))
    }
}
