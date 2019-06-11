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
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.config.amls.AMLSLoader
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector

import uk.gov.hmrc.agentsubscriptionfrontend.models.{AMLSDetails, AgentSession, Yes, YesNo}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.amls_applied_for
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.immutable.Map
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AMLSController @Inject()(
  override val authConnector: AuthConnector,
  val agentAssuranceConnector: AgentAssuranceConnector,
  override val continueUrlActions: ContinueUrlActions,
  override val sessionStoreService: SessionStoreService)(
  implicit messagesApi: MessagesApi,
  override val appConfig: AppConfig,
  override val metrics: Metrics,
  override val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionBehaviour {

  import AMLSForms._

  private val amlsBodies: Map[String, String] = AMLSLoader.load("/amls.csv")

  val showMoneyLaunderingComplianceForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          for {
            cachedAmlsDetails <- existingSession.map(_.amlsDetails)
            cachedGoBackUrl   <- sessionStoreService.fetchGoBackUrl
          } yield {
            (cachedAmlsDetails, cachedGoBackUrl) match {
              case (Some(amlsDetails), mayBeGoBackUrl) =>
                val form: Map[String, String] =
                  Map(
                    "amlsCode"         -> amlsBodies.find(_._2 == amlsDetails.supervisoryBody).map(_._1).getOrElse(""),
                    "membershipNumber" -> amlsDetails.membershipNumber,
                    "expiry.day"       -> amlsDetails.membershipExpiresOn.getDayOfMonth.toString,
                    "expiry.month"     -> amlsDetails.membershipExpiresOn.getMonthValue.toString,
                    "expiry.year"      -> amlsDetails.membershipExpiresOn.getYear.toString
                  )
                Ok(html.money_laundering_compliance(amlsForm(amlsBodies.keySet).bind(form), amlsBodies, mayBeGoBackUrl))

              case (None, _) => Ok(html.money_laundering_compliance(amlsForm(amlsBodies.keySet), amlsBodies))
            }
          }
        }
      }
    }
  }

  def submitMoneyLaunderingComplianceForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          amlsForm(amlsBodies.keys.toSet)
            .bindFromRequest()
            .fold(
              formWithErrors => {
                val form = AMLSForms.formWithRefinedErrors(formWithErrors)
                Ok(html.money_laundering_compliance(form, amlsBodies))
              },
              validForm => {
                val amlsDetails = AMLSDetails(
                  supervisoryBody = amlsBodies.getOrElse(validForm.amlsCode, throw new Exception("Invalid AMLS code")),
                  membershipNumber = validForm.membershipNumber,
                  membershipExpiresOn = validForm.expiry
                )

                sessionStoreService
                  .cacheAgentSession(existingSession.copy(amlsDetails = Some(amlsDetails)))
                  .map { _ =>
                    Redirect(routes.SubscriptionController.showCheckAnswers())
                  }
              }
            )
        }
      }
    }
  }

  val showAppliedForAmlsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          val form = amlsAppliedForYesNoForm
          sessionStoreService.fetchGoBackUrl.map { maybeGobackUrl =>
            Ok(
              amls_applied_for(
                existingSession.amlsAppliedYesNo.fold(form)(x => form.fill(YesNo.toRadioConfirm(x))),
                maybeGobackUrl)
            )
          }
        }
      }
    }
  }

  def submitAppliedForAmlsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {

          sessionStoreService.fetchGoBackUrl.flatMap { maybeGobackUrl =>
            amlsAppliedForYesNoForm.bindFromRequest.fold(
              formWithErrors => Ok(amls_applied_for(formWithErrors)),
              validForm => {
                val newValue = YesNo(validForm)
                val redirectTo =
                  if (Yes == newValue) routes.AMLSController.showAmlsApplicationDetails().url
                  else routes.AMLSController.moneyLaunderingComplianceIncomplete().url
                sessionStoreService
                  .cacheAgentSession(existingSession.copy(amlsAppliedYesNo = Some(newValue)))
                  .map(_ => Redirect(redirectTo))
              }
            )
          }
        }
      }
    }
  }

  val moneyLaunderingComplianceIncomplete = Action.async { implicit request =>
    Ok("TODO - amls incomplete")
  }

  val showAmlsApplicationDetails: Action[AnyContent] = Action.async { implicit request =>
    Ok("TODO - amls application details")
  }

  private def withManuallyAssuredAgent(agentSession: AgentSession)(body: => Future[Result])(
    implicit hc: HeaderCarrier): Future[Result] =
    agentSession.utr match {
      case Some(utr) =>
        agentAssuranceConnector.isManuallyAssuredAgent(utr).flatMap { response =>
          if (response) {
            toFuture(Redirect(routes.SubscriptionController.showCheckAnswers()))
          } else body
        }
      case None => Redirect(routes.UtrController.showUtrForm())
    }
}
