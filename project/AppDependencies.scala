import sbt._

private object AppDependencies {
  import play.core.PlayVersion
  import play.sbt.PlayImport._

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-25"  % "1.7.0",
    "uk.gov.hmrc" %% "git-client"         % "0.6.0",
    "uk.gov.hmrc" %% "github-client"      % "1.21.0",
    "uk.gov.hmrc" %% "mongo-lock"         % "5.1.0",
    "uk.gov.hmrc" %% "domain"             % "5.2.0",
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.2.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "hmrctest"           % "2.4.0"             % "test",
    "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0"             % "test",
    "org.pegdown"            % "pegdown"             % "1.5.0"             % "test",
    "com.github.tomakehurst" % "wiremock"            % "1.52"              % "test",
    "com.typesafe.play"      %% "play-test"          % PlayVersion.current % "test",
    "uk.gov.hmrc"            %% "reactivemongo-test" % "2.0.0"             % "test",
    "org.mockito"            % "mockito-all"         % "1.10.19"           % "test"
  )
}
