import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

object MicroServiceBuild extends Build with MicroService {

  val appName = "service-deployments"

  override lazy val plugins: Seq[Plugins] = Seq(
    SbtAutoBuildPlugin,
    SbtGitVersioning,
    SbtDistributablesPlugin
  )

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {
  import play.sbt.PlayImport._
  import play.core.PlayVersion

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-25"  % "1.7.0",
    "uk.gov.hmrc" %% "git-client"         % "0.6.0",
    "uk.gov.hmrc" %% "github-client"      % "1.21.0",
    "uk.gov.hmrc" %% "mongo-lock"         % "5.1.0",
    "uk.gov.hmrc" %% "domain"             % "5.2.0",
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.2.0"
  )

  trait TestDependencies {
    lazy val scope: String       = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply() =
      new TestDependencies {
        override lazy val test = Seq(
          "uk.gov.hmrc"            %% "hmrctest"           % "2.4.0"             % scope,
          "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0"             % scope,
          "org.pegdown"            % "pegdown"             % "1.5.0"             % scope,
          "com.github.tomakehurst" % "wiremock"            % "1.52"              % scope,
          "com.typesafe.play"      %% "play-test"          % PlayVersion.current % scope,
          "uk.gov.hmrc"            %% "reactivemongo-test" % "2.0.0"             % scope,
          "org.mockito"            % "mockito-all"         % "1.10.19"           % scope
        )
      }.test
  }

  def apply() = compile ++ Test()
}
