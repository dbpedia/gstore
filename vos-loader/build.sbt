organization := "org.dbpedia"

name := "databus-dataid-vos-loader"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.12.6"

lazy val Log4jVersion = "2.11.1"

val ScalatraVersion = "2.6.3"

libraryDependencies ++= Seq(
  "org.apache.jena" % "apache-jena-libs" % "3.0.0" excludeAll(
    ExclusionRule("org.slf4j", "slf4j-log4j12"),
    ExclusionRule("log4j", "log4j")
  ),
  "org.scalaz" %% "scalaz-core" % "7.2.26",
  "io.monix" %% "monix" % "2.3.3",
  "com.google.guava" % "guava" % "26.0-jre",
  "org.scalactic" %% "scalactic" % "3.0.5",
  "org.scala-lang.modules" %% "scala-xml" % "1.1.1", //for scalactic
  "com.iheart" %% "ficus" % "1.4.3",
  "com.github.pathikrit" %% "better-files" % "3.5.0",
  "com.jsuereth" %% "scala-arm" % "2.0",
  "com.markatta" %% "timeforscala" % "1.7",
  "commons-io" % "commons-io" % "2.6",
  "be.olsson" % "slack-appender" % "1.3.0",
  "com.squareup.okhttp3" % "okhttp" % "3.5.0",
  "org.apache.logging.log4j" % "log4j-core" % Log4jVersion,
  "org.apache.logging.log4j" % "log4j-api" % Log4jVersion,
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % Log4jVersion,
  "org.apache.logging.log4j" %% "log4j-api-scala" % "11.0",

  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
)

enablePlugins(JavaAppPackaging)
maintainer := "Sebastian Hellmann <hellmann@informatik.uni-leipzig.de>"

Compile / mainClass := Some("org.dbpedia.databus.vosloader.Main")
Test / mainClass := Some("org.dbpedia.databus.vosloader.TestDocumentSubmitter")

run / fork := true
run / javaOptions := Seq("-Xmx8G")

resolvers ++= Seq(
  Classpaths.typesafeReleases,
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases"),
  Resolver.mavenLocal
)
