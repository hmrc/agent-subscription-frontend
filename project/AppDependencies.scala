import sbt.*

object AppDependencies {

  private val bootstrapVer = "8.6.0"
  private val mongoVer = "1.9.0"
  private val playVer = "play-30"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-frontend-$playVer"             % bootstrapVer,
    "uk.gov.hmrc"       %% s"play-frontend-hmrc-$playVer"             % "8.5.0",
    "uk.gov.hmrc"       %% s"play-partials-$playVer"                  % "9.1.0",
    "uk.gov.hmrc"       %% "agent-mtd-identifiers"                    % "1.15.0",
    "uk.gov.hmrc"       %% s"play-conditional-form-mapping-$playVer"  % "2.0.0",
    "org.typelevel"     %% "cats-core"                                % "2.12.0",
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVer"                     % mongoVer,
    "uk.gov.hmrc"       %% s"crypto-json-$playVer"                    % "8.0.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test-$playVer"  % bootstrapVer % Test,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test-$playVer" % mongoVer     % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"        % "6.0.1"      % Test,
    "org.mockito"            %% "mockito-scala-scalatest"   % "1.17.31"    % Test,
    "org.scalamock"          %% "scalamock"                 % "6.0.0"      % Test
  )
}
