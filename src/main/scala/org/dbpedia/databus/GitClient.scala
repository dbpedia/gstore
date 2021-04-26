package org.dbpedia.databus

import java.util.Base64

import org.dbpedia.databus.RemoteGitlabHttpClient.{CreateFile, DeleteFile, FileAction, UpdateFile}
import sttp.client3._
import sttp.model.Uri
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scala.util.{Failure, Try}

trait GitClient {

  def projectExists(name: String): Boolean

  def createProject(name: String): Try[String]

  def commitFileContent(projectName: String, name: String, data: Array[Byte]): Try[String]

  def commitFileDelete(projectName: String, name: String): Try[String]

  def readFile(projectName: String, name: String): Try[Array[Byte]]

  def commitSeveralFiles(projectName: String, filenameAndData: Map[String, Array[Byte]]): Try[String]

  def deleteSeveralFiles(projectName: String, names: Seq[String]): Try[String]

}


class RemoteGitlabHttpClient(rootUser: String, rootPass: String, scheme: String, hostname: String, port: Option[Int]) extends GitClient {

  private val baseUri = port
    .map(p => Uri(scheme, hostname, p))
    .getOrElse(Uri(scheme, hostname))
  private val baseApiUri = baseUri.addPath("api", "v4")

  private lazy val backend = HttpURLConnectionBackend()
  private lazy val accessToken: String = {
    val req = authReq(rootUser, rootPass)
    backend.send(req).body match {
      case Left(e) => throw new RuntimeException(e)
      case Right(value) =>
        val flds = for {
          JObject(ch) <- parse(value)
          JField("access_token", JString(token)) <- ch
        } yield token
        flds.head
    }
  }


  override def createProject(name: String): Try[String] = Try {
    val req = withAuth(basicRequest.post(baseApiUri.addPath("projects").addParam("name", name)))
    val resp = req.send(backend)
    resp.body match {
      case Left(e) => throw new RuntimeException(e)
      case Right(value) =>
        println(value)
        val flds = for {
          JObject(chl) <- parse(value)
          JField("id", JInt(id)) <- chl
        } yield id
        flds.head.toString
    }
  }

  override def commitFileContent(projectName: String, name: String, data: Array[Byte]): Try[String] =
    commitSeveralFiles(projectName, Map(name -> data))

  override def commitFileDelete(projectName: String, name: String): Try[String] =
    deleteSeveralFiles(projectName, Seq(name))

  override def projectExists(name: String): Boolean =
    projectIdByName(name)
      .nonEmpty

  override def readFile(projectName: String, name: String): Try[Array[Byte]] =
    projectIdByName(projectName)
      .fold[Try[String]](Failure[String](new RuntimeException("no project found")))(Try[String](_))
      .flatMap(readSingleFile(_, name))

  override def commitSeveralFiles(projectName: String, filenameAndData: Map[String, Array[Byte]]): Try[String] =
    projectIdByName(projectName)
      .fold[Try[String]](Failure[String](new RuntimeException("no project found")))(Try[String](_))
      .flatMap(id =>
        commitSeveralActions(id, filenameAndData.map(p => CreateFile(p._1, p._2)).toSeq)
          .orElse(commitSeveralActions(id, filenameAndData.map(p => UpdateFile(p._1, p._2)).toSeq))
      )

  override def deleteSeveralFiles(projectName: String, names: Seq[String]): Try[String] =
    projectIdByName(projectName)
      .fold[Try[String]](Failure[String](new RuntimeException("no project found")))(Try[String](_))
      .flatMap(id => commitSeveralActions(id, names.map(p => DeleteFile(p))))

  private def projectIdByName(name: String): Option[String] = {
    val req = withAuth(
      basicRequest.get(baseApiUri
        .addPath("projects")
        .addParam("search", name))
    )
    val resp = req.send(backend)
    resp.body match {
      case Left(_) => None
      case Right(li) =>
        val ps = for {
          JArray(a) <- parse(li)
          e <- a
          JObject(chld) <- e
          JField("name", JString(n)) <- chld
          if name == n
        } yield e
        val ids = for {
          JObject(chld) <- ps
          JField("id", JInt(id)) <- chld
        } yield id
        // todo here is a risk that we get 2 or more projects with the same name,
        // then just taking the first one is not the right approach
        ids.headOption.map(i => i.toString)
    }
  }

  private def commitSeveralActions(projectId: String, actions: Seq[FileAction]): Try[String] = Try {
    val bodyJson = ("branch" -> "master") ~
      ("commit_message" -> "new file") ~
      ("actions" -> actions.map(_.toGitlabApiJson))

    val req = withAuth(
      basicRequest.post(baseApiUri
        .addPath("projects")
        .addPath(projectId)
        .addPath("repository")
        .addPath("commits"))
        .body(compact(render(bodyJson)))
        .header("Content-Type", "application/json")
    )
    val resp = req.send(backend)
    resp.body match {
      case Left(e) => throw new RuntimeException(e)
      case Right(value) =>
        val flds = for {
          JObject(ch) <- parse(value)
          JField("id", JString(id)) <- ch
        } yield id
        flds.head
    }
  }

  private def withAuth[A1, A2](req: Request[A1, A2]): Request[A1, A2] = {
    req.header("Authorization", s"Bearer $accessToken")
  }

  private def authReq(user: String, pass: String) =
    basicRequest.post(baseUri
      .addPath("oauth")
      .addPath("token")
    ).body(Map(
      "grant_type" -> "password",
      "username" -> user,
      "password" -> pass))

  private def readSingleFile(projectId: String, fn: String): Try[Array[Byte]] = Try {
    val req = withAuth(
      basicRequest.get(baseApiUri
        .addPath("projects")
        .addPath(projectId)
        .addPath("repository")
        .addPath("files")
        .addPath(fn)
        .addParam("ref", "master"))
    )
    val resp = req.send(backend)
    resp.body match {
      case Left(e) => throw new RuntimeException(e)
      case Right(value) =>
        val flds = for {
          JObject(ch) <- parse(value)
          JField("content", JString(content)) <- ch
        } yield content
        Base64.getDecoder.decode(flds.head)
    }
  }

}

object RemoteGitlabHttpClient {

  trait FileAction {
    def toGitlabApiJson: JObject
  }

  case class DeleteFile(path: String) extends FileAction {
    override def toGitlabApiJson: JObject =
      ("action" -> "delete") ~
        ("file_path" -> path)
  }

  case class CreateFile(path: String, content: Array[Byte]) extends FileAction {
    override def toGitlabApiJson: JObject =
      ("action" -> "create") ~
        ("file_path" -> path) ~
        ("encoding" -> "base64") ~
        ("content" -> new String(Base64.getEncoder.encode(content)))
  }

  case class UpdateFile(path: String, content: Array[Byte]) extends FileAction {
    override def toGitlabApiJson: JObject =
      ("action" -> "update") ~
        ("file_path" -> path) ~
        ("encoding" -> "base64") ~
        ("content" -> new String(Base64.getEncoder.encode(content)))
  }


}
