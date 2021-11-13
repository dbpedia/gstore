import Keys._
import org.dbpedia.sbt.Codegen

scalaVersion := "2.12.6"

organization := "org.dbpedia"
name := "gstore"
version := "0.2.0-SNAPSHOT"

val ScalatraVersion = "2.6.3"
val jenaVersion = "3.17.0"
val jettyVersion = "9.4.9.v20180320"

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra" % ScalatraVersion,
  "org.scalatra" %% "scalatra-swagger" % ScalatraVersion,
  "org.scalatra" %% "scalatra-json" % ScalatraVersion,

  "org.apache.jena" % "apache-jena-libs" % jenaVersion,
  "org.apache.jena" % "jena-shacl" % jenaVersion,
  "com.openlink.virtuoso" % "virtjdbc4" % "x.x.x" from "http://download3.openlinksw.com/uda/virtuoso/jdbc/virtjdbc4.jar",
  "c3p0" % "c3p0" % "0.9.1.2",

  "org.eclipse.jgit" % "org.eclipse.jgit" % "5.12.0.202106070339-r",

  "org.eclipse.jetty" % "jetty-webapp" % jettyVersion % "compile",
  "org.eclipse.jetty" % "jetty-proxy" % jettyVersion % "compile",

  "ch.qos.logback" % "logback-classic" % "1.2.3",

  "org.json4s" %% "json4s-jackson" % "3.6.10",
  "com.softwaremill.sttp.client3" %% "core" % "3.0.0-RC11",

  "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % Test,
)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case ps if ps.endsWith(".SF") => MergeStrategy.discard
  case ps if ps.endsWith(".DSA") => MergeStrategy.discard
  case ps if ps.endsWith(".RSA") => MergeStrategy.discard
  case _ => MergeStrategy.first
}
unmanagedResourceDirectories in Compile += {
  baseDirectory.value / "src/main/webapp"
}

Compile / sourceGenerators += Def.task {
  Codegen.generate(baseDirectory.value, (Compile / sourceManaged).value)
}.taskValue
