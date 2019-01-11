organization := "org.dbpedia"

name := "databus-dataid-vos-loader"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.12.6"

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
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.iheart" %% "ficus" % "1.4.3",
  "com.github.pathikrit" %% "better-files" % "3.5.0",
  "com.jsuereth" %% "scala-arm" % "2.0",
  "com.markatta" %% "timeforscala" % "1.7",
  "commons-io" % "commons-io" % "2.6",

  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
)

val configFile = taskKey[Option[String]]("external configurationFile")

val configFileDefault = Some("/opt/dataid-repo/vosloader.conf")

configFile := {
  
  Seq(sys.env.get("VOS_LOADER_CONFIG"), 
    sys.props.get("org.dbpedia.databus.vosloader.config"), 
    configFileDefault).flatten.headOption
}

Compile / run / fork := true
Compile / run / mainClass := Some("org.dbpedia.databus.vosloader.Main")
Test / run / mainClass := Some("org.dbpedia.databus.vosloader.TestDocumentSubmitter")
run / javaOptions := {

  Seq("-Xmx8G") ++ configFile.value.map(path => s"-Dorg.dbpedia.databus.vosloader.config=$path")
} 

resolvers ++= Seq(
  Classpaths.typesafeReleases,
  "Databus Archiva - Internal" at "http://95.216.13.238:8081/repository/internal/",
  Resolver.mavenLocal
)
