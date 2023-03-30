import sbt._

object AppDependencies {

  private val bootstrapVer = "7.12.0"

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-28"    % bootstrapVer,
    "uk.gov.hmrc"       %% "play-frontend-hmrc"            % "5.2.0-play-28",
    "uk.gov.hmrc"       %% "play-partials"                 % "8.3.0-play-28",
    "uk.gov.hmrc"       %% "agent-kenshoo-monitoring"      % "4.8.0-play-28",
    "uk.gov.hmrc"       %% "agent-mtd-identifiers"         % "0.52.0-play-27",
    "uk.gov.hmrc"       %% "play-conditional-form-mapping" % "1.12.0-play-28",
    "org.typelevel"     %% "cats-core"                     % "2.6.1",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % "0.74.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootstrapVer % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0"      % "test, it",
    "org.scalatestplus"      %% "mockito-3-12"            % "3.2.10.0"   % "test, it",
    "com.github.tomakehurst" % "wiremock-jre8"            % "2.26.2"     % "test, it",
    "org.scalamock"          %% "scalamock"               % "4.4.0"      % "test, it",
    "org.jsoup"              % "jsoup"                    % "1.14.2"     % "test, it",
    "com.vladsch.flexmark"   % "flexmark-all"             % "0.35.10"    % "test, it",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % "0.74.0"     % "test, it"
  )
}