/*
 * Copyright 2017 HM Revenue & Customs
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

import java.net.URLEncoder
import javax.inject.{Inject, Named, Singleton}

import play.api.mvc.{Action, Results}
import uk.gov.hmrc.agentsubscriptionfrontend.repository.KnownFactsResultMongoRepository
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.play.binders.ContinueUrl
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

@Singleton
class SignedOutController @Inject()(@Named("surveyRedirectUrl") surveyUrl: String,
                                    @Named("sosRedirectUrl") sosUrl: String,
                                    knownFactsResultMongoRepository: KnownFactsResultMongoRepository,
                                    sessionStoreService: SessionStoreService)
  extends FrontendController {

  def redirectToSos = Action.async { implicit request =>
    def encodeUtf8(str: String) = URLEncoder.encode(str, "UTF-8")

    def sosContinueUrlValue(id: Option[String], ourContinueUrl: Option[ContinueUrl]) = {
      val queryStr = (id.map(encodeUtf8), ourContinueUrl.map(_.encodedUrl)) match {
        case (Some(id), Some(url)) => s"?id=$id&continue=$url"
        case (Some(id), None) => s"?id=$id"
        case (None, Some(url)) => s"?continue=$url"
        case (None, None) => ""
      }

      encodeUtf8(s"/agent-subscription/return-after-gg-creds-created$queryStr")
    }

    for {
      knownFactOpt <- sessionStoreService.fetchKnownFactsResult
      id <- knownFactOpt match {
        case Some(x) => knownFactsResultMongoRepository.create(x).map(Option.apply)
        case None => Future.successful(None)
      }
      agentSubContinueUrl <- sessionStoreService.fetchContinueUrl
    } yield {
      Results.SeeOther(s"$sosUrl&continue=${sosContinueUrlValue(id, agentSubContinueUrl)}").withNewSession
    }
  }

  def startSurvey = Action { implicit request =>
    Results.SeeOther(surveyUrl).withNewSession
  }
}
