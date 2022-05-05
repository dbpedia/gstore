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

  "org.apache.jena" % "jena-jdbc-driver-remote" % jenaVersion,
  "com.openlink.virtuoso" % "virtjdbc4" % "x.x.x" from "http://download3.openlinksw.com/uda/virtuoso/jdbc/virtjdbc4.jar",
  "c3p0" % "c3p0" % "0.9.1.2",

  "org.eclipse.jgit" % "org.eclipse.jgit" % "5.12.0.202106070339-r",

  "org.eclipse.jetty" % "jetty-webapp" % jettyVersion % "compile",
  // below are additional jetty handlers for rewrites and proxying
  "org.eclipse.jetty" % "jetty-rewrite" % jettyVersion % "compile",
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
  case ps if ps.endsWith("java.sql.Driver") => MergeStrategy.concat
  case _ => MergeStrategy.first
}
unmanagedResourceDirectories in Compile += {
  baseDirectory.value / "src/main/webapp"
}

Compile / sourceGenerators += Def.task {
  Codegen.generate(baseDirectory.value, (Compile / sourceManaged).value)
}.taskValue

(Compile / compile) := ((Compile / compile) dependsOn genApiDocsSources).value

lazy val genApiDocsSources = taskKey[Unit]("Generates API docs sources")

// this task copies swagger.yaml to Jetty root and dowloads and copies swagger UI to Jetty root as well.
genApiDocsSources := {
  //here we use swagger-ui https://github.com/swagger-api/swagger-ui
  //todo: the line below can be used for api docs generation, but the swagger-ui is much better
  //Codegen.docs(target.value)
  val fn = "swagger.yaml"
  val swVersion = "4.1.0"
  val swaggerUIDist = url(s"https://github.com/swagger-api/swagger-ui/archive/refs/tags/v$swVersion.zip")
  val ctargetBase = crossTarget.value / "classes"
  val swagger = ctargetBase / fn

  val sw = baseDirectory.value / fn
  val filter = NameFilter.fnToNameFilter(fn => {
    fn.contains(s"$swVersion/dist/") &&
      !fn.contains("index.html") &&
      !fn.contains(".npmrc")
  })
  IO.copyFile(sw, swagger)

  val fs = IO.unzipURL(swaggerUIDist, target.value, filter, false)
  fs.foreach(f => IO.copyFile(f, ctargetBase / f.getName))
}
