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
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{AnyContent, Request, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AuditService
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{Agent, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.{AgentAssuranceConnector, AgentSubscriptionConnector}
import uk.gov.hmrc.agentsubscriptionfrontend.models.AssuranceResults._
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{AssuranceService, SessionStoreService}
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.{invasive_check_start, invasive_input_option}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.{Nino, SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future

object CheckAgencyController {
  val knownFactsForm: Form[KnownFacts] =
    Form[KnownFacts](
      mapping("utr" -> FieldMappings.utr, "postcode" -> FieldMappings.postcode)(KnownFacts.apply)(KnownFacts.unapply))
}

@Singleton
class CheckAgencyController @Inject()(
  assuranceService: AssuranceService,
  val agentAssuranceConnector: AgentAssuranceConnector,
  override val messagesApi: MessagesApi,
  override val authConnector: AuthConnector,
  val agentSubscriptionConnector: AgentSubscriptionConnector,
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

  def showCheckAgencyStatus: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withMaybeContinueUrlCached {
        mark("Count-Subscription-CheckAgency-Start")
        Future successful Ok(html.check_agency_status(CheckAgencyController.knownFactsForm))
      }
    }
  }

  val checkAgencyStatus: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      CheckAgencyController.knownFactsForm
        .bindFromRequest()
        .fold(formWithErrors => {
          Future successful Ok(html.check_agency_status(formWithErrors))
        }, knownFacts => checkAgencyStatusGivenValidForm(knownFacts))
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

    agentSubscriptionConnector.getRegistration(knownFacts.utr, knownFacts.postcode) flatMap {
      case Some(Registration(Some(taxpayerName), isSubscribedToAgentServices)) if !isSubscribedToAgentServices =>
        processCheckAgencyStatus(knownFacts.utr, taxpayerName, isSubscribedToAgentServices)
      case Some(Registration(_, isSubscribedToAgentServices)) if isSubscribedToAgentServices =>
        mark("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
        Future successful Redirect(routes.CheckAgencyController.showAlreadySubscribed())
      case Some(_) =>
        throw new IllegalStateException(s"The agency with UTR ${knownFacts.utr} has no organisation name.")
      case None =>
        mark("Count-Subscription-NoAgencyFound")
        Future successful Redirect(routes.CheckAgencyController.showNoAgencyFound())
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
            utr = knownFactsResult.utr,
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
              if (FieldMappings.isValidSaAgentCode(saAgentReference)) {
                Future.successful(Redirect(routes.CheckAgencyController.invasiveTaxPayerOptionGet())
                  .withSession(request.session + ("saAgentReferenceToCheck" -> saAgentReference)))
              } else {
                Future.successful(Ok(invasive_check_start(RadioWithInput.confirmResponseForm
                  .withError("confirmResponse-true-hidden-input", Messages("error.saAgentCode.invalid")))))
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

            val taxId = if (correctForm.value.getOrElse(false)) {
              TaxIdFormValue(
                id = correctForm.messageOfTrueRadioChoice.getOrElse(""),
                name = "nino",
                formField = "confirmResponse-true-hidden-input",
                validateId = Nino.isValid,
                stringAsTaxId = Nino.apply
              )
            } else {
              TaxIdFormValue(
                id = correctForm.messageOfFalseRadioChoice.getOrElse(""),
                name = "utr",
                formField = "confirmResponse-false-hidden-input",
                validateId = Utr.isValid,
                stringAsTaxId = Utr.apply
              )
            }

            checkAndRedirect(taxId)
          }
        )
    }
  }

  case class TaxIdFormValue(
    id: String,
    name: String,
    formField: String,
    validateId: (String) => Boolean,
    stringAsTaxId: (String) => TaxIdentifier) {
    lazy val taxId = stringAsTaxId(id)
    def isValid = validateId(id)
  }

  private def checkAndRedirect(
    value: TaxIdFormValue)(implicit hc: HeaderCarrier, request: Request[AnyContent], agent: Agent) =
    if (value.isValid) {
      request.session.get("saAgentReferenceToCheck") match {
        case Some(saAgentReference) =>
          assuranceService
            .checkActiveCesaRelationship(value.taxId, value.name, SaAgentReference(saAgentReference))
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
    } else {
      Future.successful(
        Ok(
          invasive_input_option(RadioWithInput.confirmResponseForm
            .withError(value.formField, Messages(s"error.${value.name}.invalid")))))
    }
}
