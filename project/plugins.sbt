addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.3.13")
addSbtPlugin("org.scalatra.sbt" % "sbt-scalatra" % "1.0.2")

libraryDependencies ++= Seq(
  "com.github.pathikrit" %% "better-files" % "3.5.0",
  "io.swagger" % "swagger-codegen" % "2.4.17"
)
