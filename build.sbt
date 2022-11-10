import sbt.CrossVersion
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimumStmtTotal := 80.00,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}

lazy val wartRemoverSettings = {
  val wartRemoverWarning = {
    val warningWarts = Seq(
      //Wart.JavaSerializable,
      //Wart.StringPlusAny,
      Wart.AsInstanceOf,
      Wart.IsInstanceOf
      //Wart.Any
    )
    Compile / compile / wartremoverWarnings ++= warningWarts
  }

  val wartRemoverError = {
    // Error
    val errorWarts = Seq(
      Wart.ArrayEquals,
      Wart.AnyVal,
      Wart.EitherProjectionPartial,
      //Wart.Enumeration,
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

    Compile / compile / wartremoverErrors ++= errorWarts
  }

  Seq(
    wartRemoverError,
    wartRemoverWarning,
    Test / compile / wartremoverErrors --= Seq(Wart.Any, Wart.Equals, Wart.Null, Wart.NonUnitStatements, Wart.PublicInference),
    wartremoverExcluded ++=
      (Compile / routes).value ++
        (baseDirectory.value / "it").get ++
        (baseDirectory.value / "test").get ++
        Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala")
  )
}

TwirlKeys.templateImports ++= Seq(
  "uk.gov.hmrc.agentsubscriptionfrontend.views.html.MainTemplate",
  "uk.gov.hmrc.agentsubscriptionfrontend.views.html.components._",
)

lazy val compileDeps = Seq(
  "uk.gov.hmrc"   %% "bootstrap-frontend-play-28"    % "7.8.0",
  "uk.gov.hmrc"   %% "play-frontend-hmrc"            % "3.32.0-play-28",
  "uk.gov.hmrc"   %% "play-partials"                 % "8.3.0-play-28",
  "uk.gov.hmrc"   %% "agent-kenshoo-monitoring"      % "4.8.0-play-28",
  "uk.gov.hmrc"   %% "agent-mtd-identifiers"         % "0.47.0-play-27",
  "uk.gov.hmrc"   %% "play-conditional-form-mapping" % "1.12.0-play-28",
  "uk.gov.hmrc"   %% "play-language"                 % "5.3.0-play-28",
  "org.typelevel" %% "cats-core"                     % "2.6.1",
  "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"      % "0.73.0"
)

def testDeps(scope: String) = Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % scope,
  "org.scalatestplus" %% "mockito-3-12" % "3.2.10.0" % scope,
  "com.github.tomakehurst" % "wiremock-jre8" % "2.26.2" % scope,
  "org.scalamock" %% "scalamock" % "4.4.0" % scope,
  "org.jsoup" % "jsoup" % "1.14.2" % scope,
  "com.vladsch.flexmark" % "flexmark-all" % "0.35.10" % scope,
  "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % "0.73.0" % scope
)

lazy val root = Project("agent-subscription-frontend", file("."))
  .settings(
    name := "agent-subscription-frontend",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.12.12",
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-Xlint:-missing-interpolator,_",
      "-Yno-adapted-args",
      "-Ywarn-dead-code",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions",
      "-P:silencer:pathFilters=views;routes"),
    PlayKeys.playDefaultPort := 9437,
    resolvers := Seq(
      Resolver.typesafeRepo("releases"),
    ),
    resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2",
    resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns),
    resolvers += "HMRC-local-artefacts-maven" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases-local",

libraryDependencies ++= compileDeps ++ testDeps("test") ++ testDeps("it"),
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.0" cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % "1.7.0" % Provided cross CrossVersion.full
    ),
    publishingSettings,
    scoverageSettings,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true
  )
  .configs(IntegrationTest)
  .settings(
    majorVersion := 0,
    IntegrationTest / Keys.fork := true,
    Defaults.itSettings,
    IntegrationTest / unmanagedSourceDirectories += baseDirectory(_ / "it").value,
    IntegrationTest / parallelExecution := false
  )
  .settings(wartRemoverSettings: _*)
  .enablePlugins(PlayScala, SbtDistributablesPlugin)

inConfig(IntegrationTest)(scalafmtCoreSettings)

