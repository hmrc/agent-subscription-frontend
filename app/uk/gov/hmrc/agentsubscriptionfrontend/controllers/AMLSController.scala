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
import play.api.i18n.MessagesApi
import play.api.mvc.{AnyContent, _}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent.hasNonEmptyEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.config.amls.AMLSLoader
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, PendingDate, RegDetails, SubscriptionJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.amls._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.immutable.Map
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AMLSController @Inject()(
  override val authConnector: AuthConnector,
  val agentAssuranceConnector: AgentAssuranceConnector,
  override val continueUrlActions: ContinueUrlActions,
  val sessionStoreService: SessionStoreService,
  override val subscriptionJourneyService: SubscriptionJourneyService)(
  implicit messagesApi: MessagesApi,
  override val appConfig: AppConfig,
  override val metrics: Metrics,
  override val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig, subscriptionJourneyService)
    with SessionBehaviour {

  import AMLSForms._

  private val amlsBodies: Map[String, String] = AMLSLoader.load("/amls.csv")

  def showCheckAmlsPage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          agent.getMandatorySubscriptionRecord.map { record =>
            record.amlsData match {
              case Some(amlsData) =>
                Ok(check_amls(checkAmlsForm.bind(Map("registeredAmls" -> RadioInputAnswer(amlsData.amlsRegistered)))))
              case None => Ok(check_amls(checkAmlsForm))
            }
          }
        }
      }
    }
  }

  def submitCheckAmls: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          checkAmlsForm
            .bindFromRequest()
            .fold(
              formWithErrors => Ok(html.amls.check_amls(formWithErrors)),
              validForm => {
                val nextPage: Result = validForm match {
                  case Yes =>
                    Redirect(routes.AMLSController.showAmlsDetailsForm())
                  case No => Redirect(routes.AMLSController.showCheckAmlsAlreadyAppliedForm())
                }
                updateAmlsJourneyRecord(
                  agent,
                  d => Some(d.copy(amlsRegistered = RadioInputAnswer.toBoolean(validForm))),
                  nextPage,
                  maybeCreateNewAmlsData = Some(
                    AmlsData(
                      amlsRegistered = RadioInputAnswer.toBoolean(validForm),
                      amlsAppliedFor = None,
                      supervisoryBody = None,
                      pendingDetails = None,
                      registeredDetails = None))
                )
              }
            )
        }

      }
    }
  }

  def showCheckAmlsAlreadyAppliedForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          agent.getMandatoryAmlsData.map { data =>
            data.amlsAppliedFor match {
              case Some(appliedFor) =>
                Ok(amls_applied_for(appliedForAmlsForm.bind(Map("amlsAppliedFor" -> RadioInputAnswer(appliedFor)))))
              case None => Ok(amls_applied_for(appliedForAmlsForm))
            }
          }
        }
      }
    }
  }

  def submitCheckAmlsAlreadyAppliedForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          appliedForAmlsForm.bindFromRequest.fold(
            formWithErrors => Ok(amls_applied_for(formWithErrors)),
            validForm => {
              val nextPage = validForm match {
                case Yes => Redirect(routes.AMLSController.showAmlsApplicationDatePage())
                case No  => Redirect(routes.AMLSController.showAmlsNotAppliedPage())
              }
              updateAmlsJourneyRecord(
                agent,
                d => Some(d.copy(amlsAppliedFor = Some(RadioInputAnswer.toBoolean(validForm)))),
                nextPage)
            }
          )
        }
      }
    }
  }

  def showAmlsDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          for {
            record          <- agent.getMandatoryAmlsData
            cachedGoBackUrl <- sessionStoreService.fetchGoBackUrl
          } yield
            (record.registeredDetails, record.supervisoryBody) match {
              case (Some(details), Some(supervisoryBody)) =>
                val form: Map[String, String] = Map(
                  "amlsCode"         -> amlsBodies.find(_._2 == supervisoryBody).map(_._1).getOrElse(""),
                  "membershipNumber" -> details.membershipNumber,
                  "expiry.day"       -> details.membershipExpiresOn.getDayOfMonth.toString,
                  "expiry.month"     -> details.membershipExpiresOn.getMonthValue.toString,
                  "expiry.year"      -> details.membershipExpiresOn.getYear.toString
                )
                Ok(html.amls.amls_details(amlsForm(amlsBodies.keySet).bind(form), amlsBodies, cachedGoBackUrl))

              case _ => Ok(html.amls.amls_details(amlsForm(amlsBodies.keySet), amlsBodies, cachedGoBackUrl))
            }
        }
      }
    }
  }

  def submitAmlsDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          amlsForm(amlsBodies.keys.toSet)
            .bindFromRequest()
            .fold(
              formWithErrors => {
                val form = AMLSForms.formWithRefinedErrors(formWithErrors)
                Ok(html.amls.amls_details(form, amlsBodies))
              },
              validForm => {
                val supervisoryBodyData =
                  amlsBodies.getOrElse(validForm.amlsCode, throw new Exception("Invalid AMLS code"))
                updateAmlsJourneyRecord(
                  agent,
                  d =>
                    Some(
                      d.copy(
                        supervisoryBody = Some(supervisoryBodyData),
                        registeredDetails = Some(RegDetails(validForm.membershipNumber, validForm.expiry)))),
                  Redirect(routes.TaskListController.showTaskList())
                )
              }
            )
        }
      }
    }
  }

  def showAmlsNotAppliedPage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(html.amls.amls_not_applied())
    }
  }

  def showAmlsApplicationDatePage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          for {
            cachedAmlsDetails <- agent.getMandatoryAmlsData
            cachedGoBackUrl   <- sessionStoreService.fetchGoBackUrl
          } yield {
            cachedAmlsDetails.pendingDetails match {
              case Some(pendingDetails) =>
                val form: Map[String, String] = Map(
                  "amlsCode"        -> "HMRC",
                  "appliedOn.day"   -> pendingDetails.appliedOn.getDayOfMonth.toString,
                  "appliedOn.month" -> pendingDetails.appliedOn.getMonthValue.toString,
                  "appliedOn.year"  -> pendingDetails.appliedOn.getYear.toString
                )
                Ok(
                  html.amls
                    .amls_pending_details(amlsPendingForm.bind(form), cachedGoBackUrl))

              case None =>
                Ok(
                  html.amls
                    .amls_pending_details(amlsPendingForm, cachedGoBackUrl))
            }
          }
        }
      }
    }
  }

  def submitAmlsApplicationDatePage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          amlsPendingForm
            .bindFromRequest()
            .fold(
              formWithErrors => {
                val form = AMLSForms.amlsPendingDetailsFormWithRefinedErrors(formWithErrors)
                Ok(html.amls.amls_pending_details(form))
              },
              validForm => {
                val supervisoryBodyData =
                  amlsBodies.getOrElse(validForm.amlsCode, throw new Exception("Invalid AMLS code"))

                updateAmlsJourneyRecord(
                  agent,
                  d =>
                    Some(
                      d.copy(
                        supervisoryBody = Some(supervisoryBodyData),
                        pendingDetails = Some(PendingDate(validForm.appliedOn)))),
                  Redirect(routes.TaskListController.showTaskList())
                )
              }
            )
        }
      }
    }
  }

  private def withManuallyAssuredAgent(agentSession: AgentSession)(body: => Future[Result])(
    implicit hc: HeaderCarrier): Future[Result] =
    agentSession.utr match {
      case Some(utr) =>
        agentAssuranceConnector.isManuallyAssuredAgent(utr).flatMap { response =>
          if (response) {
            sessionStoreService
              .cacheAgentSession(agentSession.copy(taskListFlags = agentSession.taskListFlags.copy(isMAA = true)))
              .flatMap(_ => toFuture(Redirect(routes.SubscriptionController.showCheckAnswers())))
          } else body
        }
      case None =>
        //redirect to task list ??? What happens if agent is on MAA List?
        Redirect(routes.BusinessDetailsController.showBusinessDetailsForm())
      //Redirect(routes.UtrController.showUtrForm())
    }

  def updateSession(existingSession: AgentSession, amlsDetails: AMLSDetails, agent: Agent)(
    implicit hc: HeaderCarrier) = {
    val newSession = agent match {
      case hasNonEmptyEnrolments(_) =>
        existingSession
          .copy(
            amlsDetails = Some(amlsDetails),
            taskListFlags = existingSession.taskListFlags.copy(amlsTaskComplete = true))
      case _ =>
        existingSession
          .copy(
            amlsDetails = Some(amlsDetails),
            taskListFlags = existingSession.taskListFlags.copy(amlsTaskComplete = true, createTaskComplete = true))
    }
    sessionStoreService.cacheAgentSession(newSession)
  }

  def updateAmlsJourneyRecord(
    agent: Agent,
    updateExistingAmlsData: AmlsData => Option[AmlsData],
    nextPage: Result,
    maybeCreateNewAmlsData: Option[AmlsData] = None)(implicit hc: HeaderCarrier): Future[Result] =
    for {
      record <- subscriptionJourneyService.getJourneyRecord(agent.authProviderId)
      updatedRecord <- record match {
                        case Some(r) =>
                          val newAmlsData: Option[AmlsData] = r.amlsData match {
                            case Some(d) => updateExistingAmlsData(d)
                            case None =>
                              if (maybeCreateNewAmlsData.isDefined) maybeCreateNewAmlsData
                              else throw new RuntimeException("No AMLS data found in record")
                          }
                          r.copy(amlsData = newAmlsData)
                        case None => throw new RuntimeException("subscription journey record expected but not found")
                      }
      _        <- subscriptionJourneyService.saveJourneyRecord(updatedRecord)
      gotoPage <- nextPage
    } yield gotoPage
}
