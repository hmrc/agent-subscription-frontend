import uk.gov.hmrc.{DefaultBuildSettings, SbtAutoBuildPlugin}


val appName = "agent-subscription-frontend"

ThisBuild / majorVersion := 2
ThisBuild / scalaVersion := "2.13.12"

val scalaCOptions = Seq(
  "-Xfatal-warnings",
  "-Xlint:-missing-interpolator,_",
  "-Ywarn-dead-code",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:implicitConversions",
  "-Wconf:src=target/.*:s", // silence warnings from compiled files
  "-Wconf:src=*html:w", // silence html warnings as they are wrong
  "-Wconf:src=routes/.*:s" // silence warnings from routes
)

TwirlKeys.templateImports ++= Seq(
  "uk.gov.hmrc.agentsubscriptionfrontend.views.html.MainTemplate",
  "uk.gov.hmrc.agentsubscriptionfrontend.views.html.components._",
)

lazy val root = (project in file("."))
  .settings(
    name := appName,
    organization := "uk.gov.hmrc",
    PlayKeys.playDefaultPort := 9437,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    resolvers ++= Seq(Resolver.typesafeRepo("releases")),
    scalacOptions ++= scalaCOptions,
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources"
  )
  .settings(
    Test / parallelExecution := false,
    CodeCoverageSettings.settings
  )
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(root % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.test)
  .settings(
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true
  )


