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

import javax.inject.{ Inject, Named, Singleton }
import com.kenshoo.play.metrics.Metrics
import play.api.{ Configuration, Logger }
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.mvc.{ AnyContent, Request, _ }
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AuditService
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{ AgentRequest, AuthActions, NoOpRegimeWithContinueUrl }
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.{ AgentAssuranceConnector, AgentSubscriptionConnector, FrontendAuthConnector }
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.{ invasive_check_start, invasive_input_option }
import uk.gov.hmrc.domain.{ Nino, SaAgentReference, TaxIdentifier }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.passcode.authentication.{ PasscodeAuthenticationProvider, PasscodeVerificationConfig }
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

object CheckAgencyController {
  val knownFactsForm: Form[KnownFacts] = Form[KnownFacts](
    mapping(
      "utr" -> FieldMappings.utr,
      "postcode" -> FieldMappings.postcode)(KnownFacts.apply)(KnownFacts.unapply))
}

@Singleton
class CheckAgencyController @Inject() (
  @Named("agentAssuranceFlag") agentAssuranceFlag: Boolean,
  val agentAssuranceConnector: AgentAssuranceConnector,
  override val messagesApi: MessagesApi,
  override val authConnector: AuthConnector,
  override val config: PasscodeVerificationConfig,
  override val passcodeAuthenticationProvider: PasscodeAuthenticationProvider,
  val agentSubscriptionConnector: AgentSubscriptionConnector,
  val sessionStoreService: SessionStoreService,
  val continueUrlActions: ContinueUrlActions,
  auditService: AuditService,
  val metrics: Metrics)(implicit configuration: Configuration)
  extends FrontendController with I18nSupport with AuthActions with SessionDataMissing with Monitoring {

  import continueUrlActions._

  val showHasOtherEnrolments: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() { implicit authContext => implicit request =>
    Future successful Ok(html.has_other_enrolments())
  }

  private def hasMtdEnrolment(implicit request: AgentRequest[_]): Boolean = request.enrolments.exists(_.key == "HMRC-AS-AGENT")

  def showCheckAgencyStatus: Action[AnyContent] = {
    AuthorisedWithSubscribingAgentAsync(NoOpRegimeWithContinueUrl) { implicit authContext => implicit request =>
      withMaybeContinueUrlCached {
        hasMtdEnrolment match {
          case true =>
            mark("Count-Subscription-AlreadySubscribed-HasEnrolment")
            Future successful Redirect(routes.CheckAgencyController.showAlreadySubscribed())
          case false =>
            mark("Count-Subscription-CheckAgency-Start")
            Future successful Ok(html.check_agency_status(CheckAgencyController.knownFactsForm))
        }
      }
    }
  }

  val checkAgencyStatus: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() { implicit authContext: AuthContext => implicit request =>
    CheckAgencyController.knownFactsForm.bindFromRequest().fold(
      formWithErrors => {
        Future successful Ok(html.check_agency_status(formWithErrors))
      },
      knownFacts => checkAgencyStatusGivenValidForm(knownFacts))
  }
  private def checkAgencyStatusGivenValidForm(knownFacts: KnownFacts)(implicit authContext: AuthContext, request: AgentRequest[AnyContent]): Future[Result] = {
    def assureIsAgent(): Future[Option[AssuranceResults]] = {
      if (agentAssuranceFlag) {
        val futurePaye = agentAssuranceConnector.hasAcceptableNumberOfPayeClients
        val futureSA = agentAssuranceConnector.hasAcceptableNumberOfSAClients

        for {
          hasAcceptableNumberOfPayeClients <- futurePaye
          hasAcceptableNumberOfSAClients <- futureSA
        } yield Some(AssuranceResults(hasAcceptableNumberOfPayeClients, hasAcceptableNumberOfSAClients))
      } else Future.successful(None)
    }

    def decideBasedOn: Option[AssuranceResults] => Result = {
      case Some(AssuranceResults(false, false)) =>
        mark("Count-Subscription-InvasiveCheck-Start")
        Redirect(routes.CheckAgencyController.invasiveCheckStart)
      case _ =>
        mark("Count-Subscription-CheckAgency-Success")
        Redirect(routes.CheckAgencyController.showConfirmYourAgency())
    }

    agentSubscriptionConnector.getRegistration(knownFacts.utr, knownFacts.postcode) flatMap { maybeRegistration: Option[Registration] =>
      maybeRegistration match {
        case Some(Registration(Some(taxpayerName), isSubscribedToAgentServices)) if !isSubscribedToAgentServices =>

          for {
            assuranceResults <- assureIsAgent()
            knownFactsResult = KnownFactsResult(knownFacts.utr, knownFacts.postcode, taxpayerName, isSubscribedToAgentServices)
            _ <- sessionStoreService.cacheKnownFactsResult(knownFactsResult)
            _ <- assuranceResults.map(auditService.sendAgentAssuranceAuditEvent(knownFactsResult, _)).getOrElse(Future.successful(()))
          } yield decideBasedOn(assuranceResults)

        case Some(Registration(_, isSubscribedToAgentServices)) if isSubscribedToAgentServices =>
          mark("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
          Future successful Redirect(routes.CheckAgencyController.showAlreadySubscribed())
        case Some(_) => throw new IllegalStateException(s"The agency with UTR ${knownFacts.utr} has no organisation name.")
        case None =>
          mark("Count-Subscription-NoAgencyFound")
          Future successful Redirect(routes.CheckAgencyController.showNoAgencyFound())
      }
    }
  }

  val showNoAgencyFound: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() { implicit authContext => implicit request =>
    Future successful Ok(html.no_agency_found())
  }

  val showConfirmYourAgency: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() { implicit authContext => implicit request =>
    sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
      Ok(html.confirm_your_agency(
        registrationName = knownFactsResult.taxpayerName,
        postcode = knownFactsResult.postcode,
        utr = knownFactsResult.utr,
        nextPageUrl = lookupNextPageUrl(knownFactsResult.isSubscribedToAgentServices)))
    }.getOrElse {
      sessionMissingRedirect()
    })
  }

  private def lookupNextPageUrl(isSubscribedToAgentServices: Boolean): String =
    if (isSubscribedToAgentServices) {
      mark("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
      routes.CheckAgencyController.showAlreadySubscribed().url
    } else {
      mark("Count-Subscription-CleanCreds-Start")
      routes.SubscriptionController.showInitialDetails().url
    }

  val showAlreadySubscribed: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() { implicit authContext => implicit request =>
    Future successful Ok(html.already_subscribed())
  }

  def invasiveCheckStart: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() { implicit authContext => implicit request =>
    Future.successful(Ok(invasive_check_start(RadioWithInput.confirmResponseForm)))
  }

  def invasiveSaAgentCodePost: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() { implicit authContext => implicit request =>
    RadioWithInput.confirmResponseForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(Ok(invasive_check_start(formWithErrors)))
      }, correctForm => {
        if (correctForm.value.getOrElse(false)) {
          val saAgentReference = correctForm.messageOfTrueRadioChoice.getOrElse("")
          if (FieldMappings.isValidSaAgentCode(saAgentReference)) {
            Future.successful(Redirect(routes.CheckAgencyController.invasiveTaxPayerOptionGet)
              .withSession(request.session + ("saAgentReferenceToCheck" -> saAgentReference)))
          } else {
            Future.successful(Ok(invasive_check_start(RadioWithInput
              .confirmResponseForm.withError("confirmResponse-true-hidden-input", Messages("error.saAgentCode.invalid")))))
          }
        } else {
          mark("Count-Subscription-InvasiveCheck-Declined")
          Future.successful(Redirect(routes.StartController.setupIncomplete()))
        }
      })
  }

  def invasiveTaxPayerOptionGet: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() { implicit authContext => implicit request =>
    Future.successful(Ok(invasive_input_option(RadioWithInput.confirmResponseForm)))
  }

  def invasiveTaxPayerOption: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() { implicit authContext => implicit request =>
    RadioWithInput.confirmResponseForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(Ok(invasive_input_option(formWithErrors)))
      }, correctForm => {

        val taxId = if (correctForm.value.getOrElse(false)) {
          TaxIdFormValue(
            id = correctForm.messageOfTrueRadioChoice.getOrElse(""),
            name = "nino",
            formField = "confirmResponse-true-hidden-input",
            validateId = Nino.isValid,
            stringAsTaxId = Nino.apply)
        } else {
          TaxIdFormValue(
            id = correctForm.messageOfFalseRadioChoice.getOrElse(""),
            name = "utr",
            formField = "confirmResponse-false-hidden-input",
            validateId = Utr.isValid,
            stringAsTaxId = Utr.apply)
        }

        checkAndRedirect(taxId)
      })
  }

  case class TaxIdFormValue(id: String, name: String, formField: String,
    validateId: (String) => Boolean,
    stringAsTaxId: (String) => TaxIdentifier) {
    lazy val taxId = stringAsTaxId(id)
    def isValid = validateId(id)
  }

  def checkAndRedirect(value: TaxIdFormValue)(implicit hc: HeaderCarrier, request: AgentRequest[AnyContent], authContext: AuthContext) = {

    if (value.isValid) {
      val saAgentReference = request.session.get("saAgentReferenceToCheck").getOrElse("")
      checkActiveCesaRelationship(value, SaAgentReference(saAgentReference)).map {
        case true =>
          mark("Count-Subscription-InvasiveCheck-Success")
          Redirect(routes.CheckAgencyController.showConfirmYourAgency())
        case false =>
          mark("Count-Subscription-InvasiveCheck-Failed")
          Redirect(routes.StartController.setupIncomplete())
      }
    } else {
      Future.successful(Ok(invasive_input_option(RadioWithInput.confirmResponseForm
        .withError(value.formField, Messages(s"error.${value.name}.invalid")))))
    }
  }

  def checkActiveCesaRelationship(
    taxIdFormValue: TaxIdFormValue,
    inputSaAgentReference: SaAgentReference)(
    implicit
    hc: HeaderCarrier, request: AgentRequest[AnyContent], authContext: AuthContext): Future[Boolean] = {
    agentAssuranceConnector.hasActiveCesaRelationship(taxIdFormValue.taxId, taxIdFormValue.name, inputSaAgentReference).map { relationshipExists =>
      val (userEnteredNino, userEnteredUtr) = taxIdFormValue.taxId match {
        case nino @ Nino(_) => (Some(nino), None)
        case utr @ Utr(_) => (None, Some(utr))
      }

      for {
        knownFactResultOpt <- sessionStoreService.fetchKnownFactsResult
        _ <- knownFactResultOpt match {
          case Some(knownFactsResult) =>
            auditService.sendAgentAssuranceAuditEvent(
              knownFactsResult,
              AssuranceResults(false, false),
              Some(AssuranceCheckInput(Some(relationshipExists), Some(inputSaAgentReference.value), userEnteredUtr, userEnteredNino)))
          case None =>
            Future.successful(Logger.warn("Could not send audit events due to empty knownfacts results"))
        }
      } yield ()

      relationshipExists
    }
  }
}