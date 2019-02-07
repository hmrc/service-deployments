import sbt._

private object AppDependencies {
  import play.core.PlayVersion
  import play.sbt.PlayImport._

  val bootstrapPlayVersion = "0.23.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-26"  % bootstrapPlayVersion,
    "uk.gov.hmrc" %% "git-client"         % "0.8.0",
    "uk.gov.hmrc" %% "mongo-lock"         % "6.7.0-play-26",
    "uk.gov.hmrc" %% "domain"             % "5.2.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-play-26"  % bootstrapPlayVersion % Test classifier "tests",
    "uk.gov.hmrc"            %% "hmrctest"           % "3.2.0"              % Test,
    "org.scalatest"          %% "scalatest"          % "3.0.4"              % Test,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2"              % Test,
    "org.pegdown"            %  "pegdown"            % "1.5.0"              % Test,
    "com.github.tomakehurst" %  "wiremock"           % "1.52"               % Test,
    "com.typesafe.play"      %% "play-test"          % PlayVersion.current  % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test" % "4.6.0-play-26"      % Test,
    "org.mockito"            %  "mockito-all"        % "1.10.19"            % Test
  )
}
