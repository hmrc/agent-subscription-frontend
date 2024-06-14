import sbt.*

object AppDependencies {

  private val bootstrapVer = "8.6.0"
  private val mongoVer = "1.9.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-30"             % bootstrapVer,
    "uk.gov.hmrc"       %% "play-frontend-hmrc-play-30"             % "8.5.0",
    "uk.gov.hmrc"       %% "play-partials-play-30"                  % "9.1.0",
    "uk.gov.hmrc"       %% "agent-mtd-identifiers"                  % "1.15.0",
    "uk.gov.hmrc"       %% "play-conditional-form-mapping-play-30"  % "2.0.0",
    "org.typelevel"     %% "cats-core"                              % "2.12.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"                     % mongoVer
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootstrapVer % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30" % mongoVer     % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"      % "6.0.1"      % Test,
    "org.mockito"            %% "mockito-scala-scalatest" % "1.17.31"    % Test,
    "org.scalamock"          %% "scalamock"               % "6.0.0"      % Test
  )
}
