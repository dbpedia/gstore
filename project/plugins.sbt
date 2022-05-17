addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.15.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-license-report" % "1.2.0")

libraryDependencies ++= Seq(
  "io.swagger.codegen.v3" % "swagger-codegen" % "3.0.30"
)
