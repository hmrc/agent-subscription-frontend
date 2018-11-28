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
import javax.inject.{Inject, Singleton}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AnyContent, _}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.config.amls.AMLSLoader
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AMLSForm, ExpiryDate}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.agentsubscriptionfrontend.views.html

import scala.concurrent.Future

//FIXME: Move to appropriate place
import play.api.data.Form
import play.api.data.Forms.{mapping, _}

@Singleton
class AMLSController @Inject()(
  override val messagesApi: MessagesApi,
  override val authConnector: AuthConnector,
  implicit override val appConfig: AppConfig,
  override val continueUrlActions: ContinueUrlActions,
  override val metrics: Metrics,
  override val sessionStoreService: SessionStoreService)
    extends FrontendController with I18nSupport with AuthActions with SessionDataMissing with Monitoring {

  val amlsBodies: Map[String, String] = AMLSLoader.load("/amls.csv")

  val amlsForm = Form[AMLSForm](
    mapping(
      "name"             -> nonEmptyText,
      "membershipNumber" -> nonEmptyText,
      "expiry" -> mapping(
        "day"   -> nonEmptyText,
        "month" -> nonEmptyText,
        "year"  -> nonEmptyText
      )(ExpiryDate.apply)(ExpiryDate.unapply)
    )(AMLSForm.apply)(AMLSForm.unapply))

  val showMoneyLaunderingComplianceForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future.successful(Ok(html.money_laundering_compliance(amlsForm, amlsBodies)))
    }
  }

  def submitMoneyLaunderingComplianceForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      Future.successful(NotImplemented)
//      businessTypeForm
//        .bindFromRequest()
//        .fold(
//          formWithErrors => {
//            if (formWithErrors.errors.exists(_.message == "error.business-type-value.invalid")) {
//              Logger.warn("Select business-type form submitted with invalid identifier")
//              throw new BadRequestException("Submitted form value did not contain valid businessType identifier")
//            }
//            Future successful Ok(html.business_type(formWithErrors))
//          },
//          validatedBusinessType => {
//            Future successful Redirect(
//              routes.BusinessIdentificationController.showBusinessDetailsForm(validatedBusinessType.businessType))
//          }
//        )
    }
  }
}
