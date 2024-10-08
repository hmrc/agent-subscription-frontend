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

import play.api.i18n.Lang
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import play.api.{Configuration, Environment, Logging}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{Agent, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AddressLookupFrontendConnector
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.ContactDetailsForms._
import uk.gov.hmrc.agentsubscriptionfrontend.form.DesAddressForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{MongoDBSessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ContactDetailsController @Inject() (
  val redirectUrlActions: RedirectUrlActions,
  val authConnector: AuthConnector,
  val sessionStoreService: MongoDBSessionStoreService,
  val metrics: Metrics,
  val config: Configuration,
  val env: Environment,
  val subscriptionJourneyService: SubscriptionJourneyService,
  addressLookUpConnector: AddressLookupFrontendConnector,
  mcc: MessagesControllerComponents,
  contactEmailCheckTemplate: contact_email_check,
  contactEmailAddressTemplate: contact_email_address,
  contactTradingNameCheckTemplate: contact_trading_name_check,
  contactTradingNameTemplate: contact_trading_name,
  contactTradingAddressCheckTemplate: contact_trading_address_check,
  addressFormWithErrorsTemplate: address_form_with_errors,
  contactPhoneCheckTemplate: contact_phone_check,
  contactTelephoneTemplate: contact_telephone
)(implicit val appConfig: AppConfig, val ec: ExecutionContext, @Named("aes") val crypto: Encrypter with Decrypter)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions with Logging {

  private val denylistedPostCodes: Set[String] = appConfig.denylistedPostcodes

  val desAddressForm = new DesAddressForm(logger, denylistedPostCodes)

  def showContactEmailCheck: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent {
      def getEmailTemplate(agent: Agent, isChanging: Option[Boolean], businessEmail: String) =
        agent.getMandatorySubscriptionRecord.contactEmailData match {
          case Some(data) =>
            Ok(
              contactEmailCheckTemplate(
                contactEmailCheckForm
                  .fill(
                    ContactEmailCheck(
                      RadioInputAnswer
                        .apply(RadioInputAnswer.apply(data.useBusinessEmail))
                    )
                  ),
                businessEmail,
                isChanging.getOrElse(false)
              )
            )
          case None =>
            Ok(contactEmailCheckTemplate(contactEmailCheckForm, businessEmail, isChanging.getOrElse(false)))
        }

      agent =>
        sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
          agent.getMandatorySubscriptionRecord.businessDetails.registration
            .flatMap(_.emailAddress)
            .fold(
              Redirect(routes.StartController.start())
            )(businessEmail => getEmailTemplate(agent, isChanging, businessEmail))
        }
    }
  }

  def submitContactEmailCheck: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        val sjr = agent.getMandatorySubscriptionRecord
        sjr.businessDetails.registration
          .flatMap(_.emailAddress)
          .fold(
            Future successful Redirect(routes.StartController.start())
          )(businessEmail =>
            contactEmailCheckForm
              .bindFromRequest()
              .fold(
                formWithErrors => Ok(contactEmailCheckTemplate(formWithErrors, businessEmail, isChanging.getOrElse(false))),
                validForm => {
                  val useBusinessEmail = RadioInputAnswer.toBoolean(validForm.check)
                  val maybeBusinessEmail = if (useBusinessEmail) Some(businessEmail) else None

                  val (check, mayBeEmail): (Boolean, Option[String]) =
                    sjr.contactEmailData
                      .fold((useBusinessEmail, maybeBusinessEmail))(data =>
                        if (useBusinessEmail) (true, maybeBusinessEmail)
                        else (false, data.contactEmail)
                      )

                  val call: Call =
                    if (!useBusinessEmail) routes.ContactDetailsController.showContactEmailAddress()
                    else if (isChanging.getOrElse(false)) routes.SubscriptionController.showCheckAnswers()
                    else routes.TaskListController.showTaskList()

                  subscriptionJourneyService
                    .saveJourneyRecord(sjr.copy(contactEmailData = Some(ContactEmailData(check, mayBeEmail))))
                    .map(_ => Redirect(call))
                }
              )
          )
      }
    }
  }

  def showContactEmailAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.getMandatorySubscriptionRecord.contactEmailData
        .fold(Redirect(routes.ContactDetailsController.showContactEmailCheck())) { contactEmailData =>
          contactEmailData.contactEmail match {
            case Some(email) =>
              Ok(
                contactEmailAddressTemplate(
                  contactEmailAddressForm.fill(BusinessEmail(email))
                )
              )
            case None => Ok(contactEmailAddressTemplate(contactEmailAddressForm))
          }
        }
    }
  }

  def changeContactEmailAddress: Action[AnyContent] = Action.async { implicit request =>
    sessionStoreService
      .cacheIsChangingAnswers(changing = true)
      .map(_ => Redirect(routes.ContactDetailsController.showContactEmailCheck()))
  }

  def submitContactEmailAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        contactEmailAddressForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(contactEmailAddressTemplate(formWithErrors)),
            validForm => {
              val sjr = agent.getMandatorySubscriptionRecord
              val emailData: Option[ContactEmailData] =
                sjr.contactEmailData
                  .map(data => ContactEmailData(data.useBusinessEmail, Some(validForm.email)))

              val redirectCall =
                if (isChanging.getOrElse(false)) routes.SubscriptionController.showCheckAnswers()
                else routes.TaskListController.showTaskList()

              subscriptionJourneyService
                .saveJourneyRecord(sjr.copy(contactEmailData = emailData))
                .map(_ => Redirect(redirectCall))
            }
          )
      }
    }
  }

  def showTradingNameCheck: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        agent.getMandatorySubscriptionRecord.businessDetails.registration
          .flatMap(_.taxpayerName)
          .fold(
            Redirect(routes.StartController.start())
          )(businessName =>
            agent.getMandatorySubscriptionRecord.contactTradingNameData match {
              case Some(data) =>
                Ok(
                  contactTradingNameCheckTemplate(
                    contactTradingNameCheckForm
                      .fill(
                        ContactTradingNameCheck(
                          RadioInputAnswer
                            .apply(RadioInputAnswer.apply(data.hasTradingName))
                        )
                      ),
                    businessName,
                    isChanging.getOrElse(false)
                  )
                )
              case None =>
                Ok(contactTradingNameCheckTemplate(contactTradingNameCheckForm, businessName, isChanging.getOrElse(false)))
            }
          )
      }
    }
  }

  def submitTradingNameCheck: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        val sjr = agent.getMandatorySubscriptionRecord
        sjr.businessDetails.registration
          .flatMap(_.taxpayerName)
          .fold(
            Future successful Redirect(routes.StartController.start())
          )(businessName =>
            contactTradingNameCheckForm
              .bindFromRequest()
              .fold(
                formWithErrors => Ok(contactTradingNameCheckTemplate(formWithErrors, businessName, isChanging.getOrElse(false))),
                validForm => {
                  val useBusinessName = RadioInputAnswer.toBoolean(validForm.check)
                  val (check, maybeTradingName): (Boolean, Option[String]) =
                    sjr.contactTradingNameData.fold((useBusinessName, Option.empty[String]))(data =>
                      if (useBusinessName) (true, None)
                      else (false, data.contactTradingName)
                    )

                  val call: Call =
                    if (!useBusinessName) routes.ContactDetailsController.showTradingName()
                    else if (isChanging.getOrElse(false)) routes.SubscriptionController.showCheckAnswers()
                    else routes.TaskListController.showTaskList()

                  subscriptionJourneyService
                    .saveJourneyRecord(sjr.copy(contactTradingNameData = Some(ContactTradingNameData(check, maybeTradingName))))
                    .map(_ => Redirect(call))
                }
              )
          )
      }
    }
  }

  def showTradingName: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.getMandatorySubscriptionRecord.contactTradingNameData
        .fold(Redirect(routes.ContactDetailsController.showTradingNameCheck())) { contactTradingData =>
          contactTradingData.contactTradingName match {
            case Some(tradingName) =>
              Ok(
                contactTradingNameTemplate(
                  contactTradingNameForm.fill(BusinessName(tradingName))
                )
              )
            case None => Ok(contactTradingNameTemplate(contactTradingNameForm))
          }
        }
    }
  }

  def changeTradingName: Action[AnyContent] = Action.async { implicit request =>
    sessionStoreService
      .cacheIsChangingAnswers(changing = true)
      .map(_ => Redirect(routes.ContactDetailsController.showTradingNameCheck()))
  }

  def submitTradingName: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        contactTradingNameForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(contactTradingNameTemplate(formWithErrors)),
            validForm => {

              val redirectCall =
                if (isChanging.getOrElse(false)) routes.SubscriptionController.showCheckAnswers()
                else
                  routes.TaskListController.showTaskList()

              subscriptionJourneyService
                .saveJourneyRecord(
                  agent.getMandatorySubscriptionRecord
                    .copy(contactTradingNameData = Some(ContactTradingNameData(false, Some(validForm.name))))
                )
                .map(_ => Redirect(redirectCall))
            }
          )
      }
    }
  }

  def showCheckMainTradingAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        agent.getMandatorySubscriptionRecord.businessDetails.registration
          .map(_.address)
          .fold(
            Redirect(routes.StartController.start())
          )(businessAddress =>
            agent.getMandatorySubscriptionRecord.contactTradingAddressData match {
              case Some(data) =>
                Ok(
                  contactTradingAddressCheckTemplate(
                    contactTradingAddressCheckForm
                      .fill(
                        ContactTradingAddressCheck(
                          RadioInputAnswer
                            .apply(RadioInputAnswer.apply(data.useBusinessAddress))
                        )
                      ),
                    formatBusinessAddress(businessAddress),
                    isChanging.getOrElse(false)
                  )
                )
              case None =>
                Ok(
                  contactTradingAddressCheckTemplate(
                    contactTradingAddressCheckForm,
                    formatBusinessAddress(businessAddress),
                    isChanging.getOrElse(false)
                  )
                )
            }
          )
      }
    }
  }

  def submitCheckMainTradingAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        val sjr = agent.getMandatorySubscriptionRecord
        sjr.businessDetails.registration
          .map(_.address)
          .fold(
            Future successful Redirect(routes.StartController.start())
          )(businessAddress =>
            contactTradingAddressCheckForm
              .bindFromRequest()
              .fold(
                formWithErrors =>
                  Ok(contactTradingAddressCheckTemplate(formWithErrors, formatBusinessAddress(businessAddress), isChanging.getOrElse(false))),
                validForm => {
                  val updatedSjr = if (validForm.check == Yes) {
                    sjr.copy(contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress))))
                  } else {
                    sjr.copy(contactTradingAddressData = Some(ContactTradingAddressData(false, None)))
                  }
                  val call: Call =
                    if (validForm.check == No) routes.ContactDetailsController.showMainTradingAddress()
                    else if (isChanging.getOrElse(false)) routes.SubscriptionController.showCheckAnswers()
                    else routes.TaskListController.showTaskList()

                  subscriptionJourneyService.saveJourneyRecord(updatedSjr).map(_ => Redirect(call))
                }
              )
          )
      }
    }
  }

  def showMainTradingAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      mark("Count-Subscription-AddressLookup-Start")
      implicit val language: Lang = mcc.messagesApi.preferred(request).lang
      addressLookUpConnector
        .initJourney(routes.ContactDetailsController.returnFromAddressLookup())
        .map(Redirect(_))
    }
  }

  def changeCheckMainTradingAddress: Action[AnyContent] = Action.async { implicit request =>
    sessionStoreService
      .cacheIsChangingAnswers(changing = true)
      .map(_ => Redirect(routes.ContactDetailsController.showCheckMainTradingAddress()))
  }

  def returnFromAddressLookup(id: String): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        val sjr = agent.getMandatorySubscriptionRecord
        sjr.businessDetails.utr match {
          case utr =>
            addressLookUpConnector.getAddressDetails(id).flatMap { address =>
              desAddressForm
                .bindAddressLookupFrontendAddress(utr, address)
                .fold(
                  formWithErrors => Future successful Ok(addressFormWithErrorsTemplate(formWithErrors, isTradingAddress = true)),
                  validDesAddress => {
                    mark("Count-Subscription-AddressLookup-Success")
                    val updatedSjr =
                      sjr.copy(contactTradingAddressData =
                        Some(
                          ContactTradingAddressData(
                            useBusinessAddress = true,
                            Some(BusinessAddress.fromDesAddress(validDesAddress))
                          )
                        )
                      )

                    val call: Call =
                      if (isChanging.getOrElse(false)) routes.SubscriptionController.showCheckAnswers()
                      else routes.TaskListController.showTaskList()

                    for {
                      _    <- subscriptionJourneyService.saveJourneyRecord(updatedSjr)
                      goto <- Redirect(call)
                    } yield goto
                  }
                )
            }
        }
      }
    }
  }

  def contactPhoneCheck: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      phoneNumberFromBusinessPartnerRecord(agent)
        .fold(Redirect(routes.ContactDetailsController.showTelephoneNumber))(_ => Redirect(routes.ContactDetailsController.showCheckTelephoneNumber))
    }
  }

  def showCheckTelephoneNumber: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        phoneNumberFromBusinessPartnerRecord(agent)
          .fold(Redirect(routes.ContactDetailsController.showTelephoneNumber))(phoneNumber =>
            Ok(contactPhoneCheckTemplate(contactPhoneCheckForm, phoneNumber, isChanging.getOrElse(false)))
          )
      }
    }
  }

  def submitCheckTelephoneNumber: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        phoneNumberFromBusinessPartnerRecord(agent)
          .fold(Future successful Redirect(routes.ContactDetailsController.showTelephoneNumber)) { phoneNumber =>
            contactPhoneCheckForm
              .bindFromRequest()
              .fold(
                hasErrors => Ok(contactPhoneCheckTemplate(hasErrors, phoneNumber, isChanging.getOrElse(false))),
                validForm => {
                  val (updatedSjr, nextPage) =
                    if (validForm.check == Yes) {
                      (
                        agent.getMandatorySubscriptionRecord
                          .copy(contactTelephoneData = Some(ContactTelephoneData(useBusinessTelephone = true, telephoneNumber = Some(phoneNumber)))),
                        if (isChanging.getOrElse(false)) routes.SubscriptionController.showCheckAnswers()
                        else routes.TaskListController.showTaskList()
                      )
                    } else {
                      (
                        agent.getMandatorySubscriptionRecord
                          .copy(contactTelephoneData = Some(ContactTelephoneData(useBusinessTelephone = false, telephoneNumber = None))),
                        routes.ContactDetailsController.showTelephoneNumber
                      )
                    }
                  for {
                    _ <- subscriptionJourneyService.saveJourneyRecord(updatedSjr)
                  } yield Redirect(nextPage)
                }
              )
          }
      }
    }
  }

  def showTelephoneNumber: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        val contactPhone: Option[String] =
          agent.getMandatorySubscriptionRecord.contactTelephoneData.flatMap(_.telephoneNumber)
        Ok(contactTelephoneTemplate(contactTelephoneForm.fill(contactPhone.getOrElse("")), getBackLinkForTelephoneNumber(agent, isChanging)))
      }
    }
  }

  def submitTelephoneNumber: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        contactTelephoneForm
          .bindFromRequest()
          .fold(
            hasErrors => Ok(contactTelephoneTemplate(hasErrors, getBackLinkForTelephoneNumber(agent, isChanging))),
            telephoneNumber => {
              val (updatedSjr, nextPage) = (
                agent.getMandatorySubscriptionRecord
                  .copy(contactTelephoneData = Some(ContactTelephoneData(useBusinessTelephone = false, telephoneNumber = Some(telephoneNumber)))),
                if (isChanging.getOrElse(false)) routes.SubscriptionController.showCheckAnswers()
                else routes.TaskListController.showTaskList()
              )
              for {
                _    <- subscriptionJourneyService.saveJourneyRecord(updatedSjr)
                goto <- Redirect(nextPage)
              } yield goto
            }
          )
      }
    }
  }

  def changeTelephoneNumber: Action[AnyContent] = Action.async { implicit request =>
    sessionStoreService
      .cacheIsChangingAnswers(changing = true)
      .map(_ => Redirect(routes.ContactDetailsController.contactPhoneCheck))
  }

  private def getBackLinkForTelephoneNumber(agent: Agent, isChanging: Option[Boolean]): Call = {
    val businessPhone: Option[String] = phoneNumberFromBusinessPartnerRecord(agent)
    if (businessPhone.isDefined) routes.ContactDetailsController.showCheckTelephoneNumber
    else if (isChanging.getOrElse(false)) routes.SubscriptionController.showCheckAnswers()
    else routes.TaskListController.showTaskList()
  }

  private def formatBusinessAddress(address: BusinessAddress): List[String] =
    List(Some(address.addressLine1), address.addressLine2, address.addressLine3, address.addressLine4, address.postalCode).flatten

  private def phoneNumberFromBusinessPartnerRecord(agent: Agent): Option[String] =
    agent.getMandatorySubscriptionRecord.businessDetails.registration.flatMap(_.primaryPhoneNumber)
}
