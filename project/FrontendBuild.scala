import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object FrontendBuild extends Build with MicroService {

    import com.typesafe.sbt.web.SbtWeb.autoImport._
    import sbt.Keys._

  val appName = "agent-subscription-frontend"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test() ++ test("it")

  override lazy val playSettings: Seq[Setting[_]] = Seq(

    // Add the views to the dist
    unmanagedResourceDirectories in Assets += baseDirectory.value / "app" / "assets",
    // Dont include the source assets in the dist package (public folder)
    excludeFilter in Assets := "tasks" || "karma.conf.js" || "tests" || "gulpfile.js*" || "js*" || "src*" || "node_modules*" || "sass*" || "typescript*" || "typings*" || ".jshintrc" || "package.json" || "tsconfig.json" || "tsd.json"
  ) ++ JavaScriptBuild.javaScriptUiSettings


  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "domain" % "4.1.0",
    "uk.gov.hmrc" %% "frontend-bootstrap" % "7.22.0",
    "uk.gov.hmrc" %% "play-partials" % "5.3.0",
    "uk.gov.hmrc" %% "play-authorised-frontend" % "6.3.0",
    "uk.gov.hmrc" %% "play-config" % "4.3.0",
    "uk.gov.hmrc" %% "logback-json-logger" % "3.1.0",
    "uk.gov.hmrc" %% "govuk-template" % "5.2.0",
    "uk.gov.hmrc" %% "play-health" % "2.1.0",
    "uk.gov.hmrc" %% "play-ui" % "7.2.1",
    "uk.gov.hmrc" %% "http-caching-client" % "6.2.0",
    "uk.gov.hmrc" %% "passcode-verification" % "4.1.0",
    "uk.gov.hmrc" %% "agent-mtd-identifiers" % "0.4.0",
    "org.typelevel" %% "cats" % "0.9.0"
  )

  def test(scope: String = "test") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "2.3.0" % scope,
    "com.github.tomakehurst" % "wiremock" % "2.3.1" % scope,
    "org.scalatest" %% "scalatest" % "2.2.6" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.jsoup" % "jsoup" % "1.8.1" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % scope,
    "org.mockito" % "mockito-core" % "2.7.4" % scope
  )

}
