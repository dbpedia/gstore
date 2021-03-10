import Keys._
import org.dbpedia.sbt.Codegen


organization := "org.dbpedia"

name := "databus-dataid-repo"

version := "0.2.0-SNAPSHOT"

scalaVersion := "2.12.6"

val ScalatraVersion = "2.6.3"
val jenaVersion = "3.17.0"

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra" % ScalatraVersion,
  "org.scalatra" %% "scalatra-swagger"  % ScalatraVersion,
  "org.scalatra" %% "scalatra-json" % ScalatraVersion,
  "org.json4s" %% "json4s-jackson" % "3.6.10",
  "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % Test,
  "com.softwaremill.sttp.client3" %% "core" % "3.0.0-RC11",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime",
  "org.eclipse.jetty" % "jetty-webapp" % "9.4.9.v20180320" % "container",
  "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"
)

libraryDependencies ++= Seq(
  "org.apache.jena" % "apache-jena-libs" % jenaVersion,
  "org.apache.jena" % "jena-shacl" % jenaVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
)

enablePlugins(ScalatraPlugin)

Compile / sourceGenerators += Def.task {
  Codegen.generate(baseDirectory.value, (Compile / sourceManaged).value)
}.taskValue
