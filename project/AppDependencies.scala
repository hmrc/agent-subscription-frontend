import sbt.*

object AppDependencies {

  private val bootstrapVer = "10.1.0"
  private val mongoVer = "2.7.0"
  private val playVer = "play-30"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-frontend-$playVer"             % bootstrapVer,
    "uk.gov.hmrc"       %% s"play-frontend-hmrc-$playVer"             % "12.10.0",
    "uk.gov.hmrc"       %% s"play-partials-$playVer"                  % "10.1.0",
    "uk.gov.hmrc"       %% s"play-conditional-form-mapping-$playVer"  % "3.3.0",
    "org.typelevel"     %% "cats-core"                                % "2.13.0",
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVer"                     % mongoVer,
    "uk.gov.hmrc"       %% "domain-play-30"                        % "11.0.0",
    "uk.gov.hmrc"       %% s"crypto-json-$playVer"                    % "8.3.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test-$playVer"  % bootstrapVer % Test,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test-$playVer" % mongoVer     % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"        % "7.0.2"      % Test,
    "org.mockito"            %% "mockito-scala-scalatest"   % "2.0.0"    % Test,
    "org.scalamock"          %% "scalamock"                 % "7.4.1"      % Test
  )
}
