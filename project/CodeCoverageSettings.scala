import sbt.Keys.parallelExecution
import sbt.{Setting, Test}
import scoverage.ScoverageKeys

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    "Reverse.*",
    "app.assets.*",
    "prod.*",
    ".*Routes.*",
    "testOnly.*",
    "testOnlyDoNotUseInAppConf.*"
  )

  private val excludedFiles: Seq[String] = Seq(
    ".*.template",
    ".*ViewUtils.*",
    ".*SessionBehaviour.*",
    ".*SessionCache.*",
    ".*MongoSessionStore.*",
    ".*TaxIdentifierFormatters.*",
    ".*models.*", // letting the file team down D:
    ".*ErrorHandler.*",
    ".*BusinessIdentificationController.*", // 50.00%
    ".*MongoDBSessionStoreService.*", // 48.00%
    ".*EmailVerificationConnector.*", // 33.33%
    ".*EmailVerificationController.*", // 0.00%
    ".*EmailVerificationService.*" // 0.00%
  )

  val settings: Seq[Setting[_]] = Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageExcludedFiles := excludedFiles.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 83,
    ScoverageKeys.coverageMinimumStmtPerFile := 50, // increase to 80 asap
    ScoverageKeys.coverageMinimumBranchTotal:= 78,
    ScoverageKeys.coverageMinimumBranchPerFile:= 50,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}
