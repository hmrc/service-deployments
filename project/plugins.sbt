resolvers += Resolver.url("hmrc-sbt-plugin-deployments", url("https://dl.bintray.com/hmrc/sbt-plugin-deployments"))(Resolver.ivyStylePatterns)

resolvers += "Typesafe Deployments" at "http://repo.typesafe.com/typesafe/deployments/"

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.4.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "0.9.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "1.0.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.8")
