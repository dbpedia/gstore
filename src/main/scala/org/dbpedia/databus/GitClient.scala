package org.dbpedia.databus

import org.dbpedia.databus.RemoteGitlabHttpClient.{CreateFile, DeleteFile, FileAction, UpdateFile}
import sttp.client3._
import sttp.model.Uri
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scalaj.http.Base64

import scala.util.{Failure, Try}

trait GitClient {

  def projectExists(name: String): Boolean

  def createProject(name: String): Try[String]

  def commitFileContent(projectName: String, name: String, data: Array[Byte]): Try[String]

  def commitFileDelete(projectName: String, name: String): Try[String]

}


class RemoteGitlabHttpClient(accessToken: String, scheme: String, hostname: String, port: Option[Int]) extends GitClient {

  private lazy val backend = HttpURLConnectionBackend()
  private val baseUri = port
    .map(p => Uri(scheme, hostname, p))
    .getOrElse(Uri(scheme, hostname)).addPath("api", "v4")

  override def createProject(name: String): Try[String] = Try {
    val req = withAuth(basicRequest.post(baseUri.addPath("projects").addParam("name", name)))
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
    projectIdByName(projectName)
      .fold[Try[String]](Failure[String](new RuntimeException("no project found")))(Try[String](_))
      .flatMap(id =>
        commitSingleAction(id, CreateFile(name, data))
          .orElse(commitSingleAction(id, UpdateFile(name, data)))
      )

  override def commitFileDelete(projectName: String, name: String): Try[String] =
    projectIdByName(projectName)
      .fold[Try[String]](Failure[String](new RuntimeException("no project found")))(Try[String](_))
      .flatMap(commitSingleAction(_, DeleteFile(name)))

  override def projectExists(name: String): Boolean = {
    projectIdByName(name).nonEmpty
  }

  private def projectIdByName(name: String): Option[String] = {
    val req = withAuth(
      basicRequest.get(baseUri
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

  private def commitSingleAction(projectId: String, action: FileAction): Try[String] = Try {
    val bodyJson = ("branch" -> "master") ~
      ("commit_message" -> "new file") ~
      ("actions" -> List(
        action.toGitlabApiJson
      ))
    val req = withAuth(
      basicRequest.post(baseUri
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
        ("content" -> String.copyValueOf(Base64.encode(content)))
  }

  case class UpdateFile(path: String, content: Array[Byte]) extends FileAction {
    override def toGitlabApiJson: JObject =
      ("action" -> "update") ~
        ("file_path" -> path) ~
        ("encoding" -> "base64") ~
        ("content" -> String.copyValueOf(Base64.encode(content)))
  }


}
