import better.files.File

organization := "org.dbpedia"

name := "databus-dataid-repo"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.12.6"

val ScalatraVersion = "2.6.3"

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra" % ScalatraVersion,
  "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % Test,
  "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime",
  "org.eclipse.jetty" % "jetty-webapp" % "9.4.9.v20180320" % "container",
  "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"
)

libraryDependencies ++= Seq(
  ("org.dbpedia.databus" % "databus-shared-lib" % "0.2").changing(),
  "org.eclipse.jgit" % "org.eclipse.jgit" % "5.0.1.201806211838-r",
  "org.apache.jena" % "apache-jena-libs" % "3.8.0",
  "org.scalaz" %% "scalaz-core" % "7.2.26",
  "io.monix" %% "monix" % "2.3.3",
  "com.google.guava" % "guava" % "26.0-jre",
  "org.scalactic" %% "scalactic" % "3.0.5",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "com.iheart" %% "ficus" % "1.4.3",
  "com.github.pathikrit" %% "better-files" % "3.5.0",
  "com.jsuereth" %% "scala-arm" % "2.0",
  "org.scalaj" %% "scalaj-http" % "2.4.1",
  "org.scalamock" %% "scalamock" % "4.1.0" % Test
)

isTestDeployment := true

//conflictManager := ConflictManager.strict

enablePlugins(SbtTwirl)
enablePlugins(ScalatraPlugin)

scalacOptions ++= Seq(
  //  "-Xfatal-warnings",
  "-Ypartial-unification"
)

containerPort in Jetty := 8088

resolvers ++= Seq(
  Classpaths.typesafeReleases,
  "Databus Archiva - Internal" at "http://95.216.13.238:8081/repository/internal/",
  Resolver.mavenLocal
)

updateOptions := updateOptions.value
  .withCachedResolution(false)

val updateBoth = taskKey[Unit]("update, then updateClassifiers")

updateBoth := Def.sequential(
  update,
  updateClassifiers,
).value

val isTestDeployment = settingKey[Boolean]("flag for test deployment")

val warTargetLocation = settingKey[String]("location to copy a war to for deployment")

val packageAndDeployToTomcat = taskKey[Unit]("package the war and copy it to the Tomcat wabapps dir")

warTargetLocation := {
  
  def warTargetFilename = if(isTestDeployment.value) "dataid-repo-test.war" else "dataid-repo.war"
  
  s"/var/lib/tomcat8/webapps/$warTargetFilename"
}

packageAndDeployToTomcat := {

  val warSourceLocation = File(sbt.Keys.`package`.value.toPath)

  val targetLocation = Some(File(warTargetLocation.value)).collect {

    case dir if dir.isDirectory => dir / warSourceLocation.name

    case file => file
  }

  targetLocation foreach { target =>

    warSourceLocation.copyTo(target, overwrite = true)
  }
}
