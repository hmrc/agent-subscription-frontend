import sbt.Tests.{Group, SubProcess}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.SbtAutoBuildPlugin

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimum := 80.00,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

lazy val wartRemoverSettings = {
  val wartRemoverWarning = {
    val warningWarts = Seq(
      Wart.JavaSerializable,
      //Wart.StringPlusAny,
      Wart.AsInstanceOf,
      Wart.IsInstanceOf
      //Wart.Any
    )
    wartremoverWarnings in (Compile, compile) ++= warningWarts
  }

  val wartRemoverError = {
    // Error
    val errorWarts = Seq(
      Wart.ArrayEquals,
      Wart.AnyVal,
      Wart.EitherProjectionPartial,
      Wart.Enumeration,
      Wart.ExplicitImplicitTypes,
      Wart.FinalVal,
      Wart.JavaConversions,
      Wart.JavaSerializable,
      //Wart.LeakingSealed,
      Wart.MutableDataStructures,
      Wart.Null,
      //Wart.OptionPartial,
      Wart.Recursion,
      Wart.Return,
      //Wart.TraversableOps,
      Wart.TryPartial,
      Wart.Var,
      Wart.While)

    wartremoverErrors in (Compile, compile) ++= errorWarts
  }

  Seq(
    wartRemoverError,
    wartRemoverWarning,
    wartremoverErrors in (Test, compile) --= Seq(Wart.Any, Wart.Equals, Wart.Null, Wart.NonUnitStatements, Wart.PublicInference),
    wartremoverExcluded ++=
    routes.in(Compile).value ++
    (baseDirectory.value / "it").get ++
    (baseDirectory.value / "test").get ++
    Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala")
  )
}

lazy val compileDeps = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-play-25" % "4.12.0",
  "uk.gov.hmrc" %% "govuk-template" % "5.35.0-play-25",
  "uk.gov.hmrc" %% "play-ui" % "7.40.0-play-25",
  "uk.gov.hmrc" %% "auth-client" % "2.21.0-play-25",
  "uk.gov.hmrc" %% "play-partials" % "6.9.0-play-25",
  "uk.gov.hmrc" %% "agent-kenshoo-monitoring" % "3.8.0",
  "uk.gov.hmrc" %% "agent-mtd-identifiers" % "0.16.0-play-25",
  "uk.gov.hmrc" %% "simple-reactivemongo" % "7.20.0-play-25",
  "uk.gov.hmrc" %% "http-caching-client" % "8.4.0-play-25",
  "uk.gov.hmrc" %% "play-conditional-form-mapping" % "0.2.0",
  "com.kenshoo" %% "metrics-play" % "2.5.9_0.5.1",
  "org.typelevel" %% "cats" % "0.9.0"
)

def tmpMacWorkaround(): Seq[ModuleID] =
  if (sys.props.get("os.name").fold(false)(_.toLowerCase.contains("mac")))
    Seq("org.reactivemongo" % "reactivemongo-shaded-native" % "0.16.1-osx-x86-64" % "runtime,test,it")
  else Seq()

def testDeps(scope: String) = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "3.8.0-play-25" % scope,
  "org.scalatest" %% "scalatest" % "3.0.7" % scope,
  "org.mockito" % "mockito-core" % "2.27.0" % scope,
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
  "com.github.tomakehurst" % "wiremock" % "2.24.0" % scope,
  "uk.gov.hmrc" %% "reactivemongo-test" % "4.14.0-play-25" % scope,
  "org.scalamock" %% "scalamock" % "4.1.0" % scope,
  "org.jsoup" % "jsoup" % "1.12.1" % scope
)

lazy val root = Project("agent-subscription-frontend", file("."))
  .settings(
    name := "agent-subscription-frontend",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.11.11",
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-Xlint:-missing-interpolator,_",
      "-Yno-adapted-args",
      "-Ywarn-value-discard",
      "-Ywarn-dead-code",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions"),
    PlayKeys.playDefaultPort := 9437,
    resolvers := Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.bintrayRepo("hmrc", "release-candidates"),
      Resolver.typesafeRepo("releases"),
      Resolver.jcenterRepo
    ),
    libraryDependencies ++= tmpMacWorkaround ++ compileDeps ++ testDeps("test") ++ testDeps("it"),
    publishingSettings,
    scoverageSettings,
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    scalafmtOnCompile in Compile := true,
    scalafmtOnCompile in Test := true
  )
  .configs(IntegrationTest)
  .settings(
    majorVersion := 0,
    Keys.fork in IntegrationTest := true,
    Defaults.itSettings,
    unmanagedSourceDirectories in IntegrationTest += baseDirectory(_ / "it").value,
    parallelExecution in IntegrationTest := false,
    scalafmtOnCompile in IntegrationTest := true
  )
  .settings(wartRemoverSettings: _*)
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)

inConfig(IntegrationTest)(scalafmtCoreSettings)


