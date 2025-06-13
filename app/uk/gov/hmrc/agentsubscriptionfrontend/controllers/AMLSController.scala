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

import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{Agent, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.config.amls.AMLSLoader
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.AmlsData
import uk.gov.hmrc.agentsubscriptionfrontend.service.AmlsValidationResult._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{AmlsService, MongoDBSessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.amls._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.LocalDate
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AMLSController @Inject() (
  val config: Configuration,
  val metrics: Metrics,
  val authConnector: AuthConnector,
  val env: Environment,
  val redirectUrlActions: RedirectUrlActions,
  val subscriptionJourneyService: SubscriptionJourneyService,
  val sessionStoreService: MongoDBSessionStoreService,
  amlsService: AmlsService,
  mcc: MessagesControllerComponents,
  checkAmlsTemplate: check_amls,
  amlsEnterNumber: amls_enter_number,
  amlsAppliedForTemplate: amls_applied_for,
  amlsNotAppliedTemplate: amls_not_applied,
  amlsDetailsTemplate: amls_details,
  amlsEnterRenewalDate: amls_enter_renewal_date,
  amlsDetailsNotFoundTemplate: amls_details_not_found,
  amlsNumberNotFoundTemplate: amls_number_not_found,
  amlsDateNotMatchedTemplate: amls_date_not_matched,
  amlsRecordIneligibleStatusTemplate: amls_record_ineligible_status
)(implicit val appConfig: AppConfig, val ec: ExecutionContext, @Named("aes") val crypto: Encrypter with Decrypter)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  import AMLSForms._

  private val amlsBodies: Map[String, String] = AMLSLoader.load("/amls.csv")
  private val hmrcAmlsCode = "HMRC"

  def changeAmlsDetails: Action[AnyContent] = Action.async { implicit request =>
    sessionStoreService
      .cacheIsChangingAnswers(changing = true)
      .map(_ => Redirect(routes.AMLSController.showAmlsRegisteredPage()))
  }

  def showAmlsRegisteredPage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChange =>
        agent.getMandatorySubscriptionRecord.amlsData match {
          case Some(amlsData) =>
            Ok(
              checkAmlsTemplate(
                checkAmlsForm.bind(Map("registeredAmls" -> RadioInputAnswer(amlsData.amlsRegistered))),
                isChange = isChange.getOrElse(false)
              )
            )
          case None => Ok(checkAmlsTemplate(checkAmlsForm, isChange.getOrElse(false)))
        }
      }
    }
  }

  def submitAmlsRegistered: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChange =>
        checkAmlsForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(checkAmlsTemplate(formWithErrors, isChange.getOrElse(false))),
            validForm => {
              val continue: Call = validForm match {
                case Yes => routes.AMLSController.showAmlsDetailsForm()
                case No  => routes.AMLSController.showCheckAmlsAlreadyAppliedForm()
              }
              val cleanAmlsData = AmlsData(amlsRegistered = RadioInputAnswer.toBoolean(validForm), amlsAppliedFor = None, amlsDetails = None)

              updateAmlsJourneyRecord(
                agent,
                amlsData =>
                  if (amlsData.amlsRegistered == RadioInputAnswer.toBoolean(validForm)) Some(amlsData)
                  else Some(cleanAmlsData),
                maybeCreateNewAmlsData = Some(cleanAmlsData)
              ).map(_ => Redirect(continueOrStop(continue, routes.AMLSController.showAmlsRegisteredPage())))
            }
          )
      }
    }
  }

  def showCheckAmlsAlreadyAppliedForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.getMandatoryAmlsData.amlsAppliedFor match {
        case Some(appliedFor) =>
          Ok(amlsAppliedForTemplate(appliedForAmlsForm.bind(Map("amlsAppliedFor" -> RadioInputAnswer(appliedFor)))))
        case None => Ok(amlsAppliedForTemplate(appliedForAmlsForm))
      }
    }
  }

  def submitCheckAmlsAlreadyAppliedForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      appliedForAmlsForm
        .bindFromRequest()
        .fold(
          formWithErrors => Ok(amlsAppliedForTemplate(formWithErrors)),
          validForm => {
            val continue = validForm match {
              case Yes => routes.AMLSController.showAmlsApplicationEnterNumberPage()
              case No  => routes.AMLSController.showAmlsNotAppliedPage()
            }
            updateAmlsJourneyRecord(agent, amlsData => Some(amlsData.copy(amlsAppliedFor = Some(RadioInputAnswer.toBoolean(validForm)))))
              .map(_ => Redirect(continueOrStop(continue, routes.AMLSController.showCheckAmlsAlreadyAppliedForm())))
          }
        )
    }
  }

  def showAmlsDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.getMandatoryAmlsData.amlsDetails match {
        case Some(amlsDetails) =>
          if (amlsDetails.isPending) {
            Ok(amlsDetailsTemplate(amlsForm(amlsBodies.keySet), amlsBodies))
          } else {
            val membershipNumber: String =
              amlsDetails.membershipNumber.getOrElse(throw new IllegalStateException("AMLS registered details without a membership number"))
            val form: Map[String, String] = Map(
              "amlsCode"         -> amlsBodies.find(_._2 == amlsDetails.supervisoryBody).map(_._1).getOrElse(""),
              "membershipNumber" -> membershipNumber,
              "expiry.day"       -> amlsDetails.membershipExpiresOn.fold("")(_.getDayOfMonth.toString),
              "expiry.month"     -> amlsDetails.membershipExpiresOn.fold("")(_.getMonthValue.toString),
              "expiry.year"      -> amlsDetails.membershipExpiresOn.fold("")(_.getYear.toString)
            )
            Ok(amlsDetailsTemplate(amlsForm(amlsBodies.keySet).bind(form), amlsBodies))
          }
        case _ => Ok(amlsDetailsTemplate(amlsForm(amlsBodies.keySet), amlsBodies))
      }
    }
  }

  def submitAmlsDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        amlsForm(amlsBodies.keys.toSet)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val form = AMLSForms.formWithRefinedErrors(formWithErrors)
              Ok(amlsDetailsTemplate(form, amlsBodies))
            },
            validForm =>
              amlsService.validateAmlsSubscription(validForm).flatMap {
                case AmlsSuspended | _: AmlsCheckFailed => Redirect(routes.AMLSController.showAmlsRecordIneligibleStatus())
                case DateNotMatched | RecordNotFound    => Redirect(routes.AMLSController.showAmlsDetailsNotFound())
                case ResultOK(safeId) =>
                  dealWithResultOkAmls(agent, isChanging, validForm.membershipNumber, validForm.amlsCode, Some(validForm.expiry), safeId)
                case ResultOKButCheckDate(_) => Redirect(routes.AMLSController.showAmlsDetailsNotFound())
              }
          )
      }
    }
  }

  private def dealWithResultOkAmls(
    agent: Agent,
    isChanging: Option[Boolean],
    membershipNumber: String,
    amlsCode: String,
    expiry: Option[LocalDate],
    safeId: Option[String]
  )(implicit ec: ExecutionContext, r: Request[AnyContent]) = {

    val supervisoryBodyData =
      amlsBodies.getOrElse(amlsCode, throw new Exception("Invalid AMLS code"))

    val continue = toTaskListOrCheckYourAnswers(isChanging)
    updateAmlsJourneyRecord(
      agent,
      amlsData =>
        Some(
          amlsData.copy(
            amlsDetails = Some(
              AmlsDetails(
                supervisoryBodyData,
                membershipNumber = Some(membershipNumber),
                membershipExpiresOn = expiry,
                amlsSafeId = safeId,
                agentBPRSafeId = agent.getMandatorySubscriptionRecord.businessDetails.registration.flatMap(_.safeId),
                appliedOn = None
              )
            )
          )
        )
    ).map(_ => Redirect(continueOrStop(continue, routes.AMLSController.showAmlsDetailsForm())))

  }

  def showAmlsDetailsNotFound: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(amlsDetailsNotFoundTemplate())
    }
  }

  def showAmlsNumberNotFound: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(amlsNumberNotFoundTemplate())
    }
  }
  def showAmlsDateNotMatched: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(amlsDateNotMatchedTemplate())
    }
  }
  def showAmlsRecordIneligibleStatus: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(amlsRecordIneligibleStatusTemplate())
    }
  }

  def showAmlsNotAppliedPage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(amlsNotAppliedTemplate())
    }
  }

  def showAmlsApplicationEnterNumberPage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(amlsEnterNumber(amlsEnterNumberForm()))
    }
  }

  def submitAmlsApplicationEnterNumberPage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        amlsEnterNumberForm()
          .bindFromRequest()
          .fold(
            formWithErrors => {

              val formWithError = formWithErrors.copy(errors = formWithErrors.errors.take(1))
              Ok(amlsEnterNumber(formWithError))
            },
            validForm =>
              amlsService.checkAmlsNumber(validForm.membershipNumber, None).flatMap {
                case AmlsSuspended | _: AmlsCheckFailed | DateNotMatched => Redirect(routes.AMLSController.showAmlsRecordIneligibleStatus())
                case RecordNotFound                                      => Redirect(routes.AMLSController.showAmlsNumberNotFound())
                case ResultOK(safeId) =>
                  dealWithResultOkAmls(agent, isChanging, validForm.membershipNumber, hmrcAmlsCode, None, safeId)
                case ResultOKButCheckDate(safeId) =>
                  sessionStoreService
                    .cacheAmlsSession(AmlsSession(validForm.membershipNumber, safeId))
                    .flatMap(_ => Redirect(routes.AMLSController.showAmlsApplicationEnterDatePage()))

              }
          )
      }
    }
  }
  def showAmlsApplicationEnterDatePage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.getMandatoryAmlsData.amlsDetails.flatMap(_.membershipExpiresOn) match {
        case Some(expiry) =>
          val formData = Map(
            "expiry.day"   -> expiry.getDayOfMonth.toString,
            "expiry.month" -> expiry.getMonthValue.toString,
            "expiry.year"  -> expiry.getYear.toString
          )
          Ok(amlsEnterRenewalDate(enterAmlsExpiryDateForm.bind(formData)))
        case None =>
          Ok(amlsEnterRenewalDate(enterAmlsExpiryDateForm))
      }
    }
  }

  def submitAmlsApplicationDatePage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchAmlsSession.flatMap { maybeAmlsSession =>
        sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
          enterAmlsExpiryDateForm
            .bindFromRequest()
            .fold(
              formWithErrors => {

                val form = AMLSForms.amlsPendingDetailsFormWithRefinedErrors(formWithErrors)

                Ok(amlsEnterRenewalDate(form))
              },
              validForm => {

                val amlsSession = maybeAmlsSession.get
                val expiryDate = validForm.expiry
                val membershipNumber = amlsSession.membershipNumber
                amlsService.checkAmlsExpiryDate(membershipNumber, expiryDate).flatMap {
                  case ResultOK(amlsSafeId) =>
                    dealWithResultOkAmls(agent, isChanging, membershipNumber, hmrcAmlsCode, Some(expiryDate), amlsSafeId)
                  case DateNotMatched => Future.successful(Redirect(routes.AMLSController.showAmlsDateNotMatched()))
                  case _ =>
                    Future.successful(Redirect(routes.AMLSController.showAmlsRecordIneligibleStatus()))
                }
              }
            )
        }
      }
    }
  }

  private def toTaskListOrCheckYourAnswers(isChanging: Option[Boolean]) =
    if (isChanging.getOrElse(false)) routes.SubscriptionController.showCheckAnswers()
    else routes.TaskListController.showTaskList()

  private def updateAmlsJourneyRecord(
    agent: Agent,
    updateExistingAmlsData: AmlsData => Option[AmlsData],
    maybeCreateNewAmlsData: Option[AmlsData] = None
  )(implicit rh: RequestHeader): Future[Unit] = {

    val record = agent.getMandatorySubscriptionRecord
    val updatedRecord = {
      val newAmlsData: Option[AmlsData] = record.amlsData match {
        case Some(amlsData) => updateExistingAmlsData(amlsData)
        case None =>
          if (maybeCreateNewAmlsData.isDefined) maybeCreateNewAmlsData
          else throw new RuntimeException("No AMLS data found in record")
      }
      record.copy(amlsData = newAmlsData)
    }
    subscriptionJourneyService.saveJourneyRecord(updatedRecord).map(_ => ())
  }
}
