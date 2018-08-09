val ScalatraVersion = "2.6.3"

organization := "org.dbpedia"

name := "Databus DataID Repo"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.12.6"

resolvers += Classpaths.typesafeReleases

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra" % ScalatraVersion,
  "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime",
  "org.eclipse.jetty" % "jetty-webapp" % "9.4.9.v20180320" % "container",
  "com.github.jsimone" % "webapp-runner" % "8.5.32.0" % "provided",
  "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"
)

libraryDependencies ++= Seq(
  "org.eclipse.jgit" % "org.eclipse.jgit" % "5.0.1.201806211838-r",
  "org.apache.jena" % "jena-arq" % "3.8.0",
  "org.scalaz" %% "scalaz-core" % "7.2.25",
  "org.scalactic" %% "scalactic" % "3.0.5",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "com.github.pathikrit" %% "better-files" % "3.5.0",
  "com.jsuereth" %% "scala-arm" % "2.0",
  "org.scalaj" %% "scalaj-http" % "2.4.1" % "test"
)

//conflictManager := ConflictManager.strict

enablePlugins(SbtTwirl)
enablePlugins(ScalatraPlugin)

containerPort in Jetty := 8088
