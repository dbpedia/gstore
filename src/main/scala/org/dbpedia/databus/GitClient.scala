package org.dbpedia.databus


import java.nio.file.{Files, Path, Paths}
import java.util.Base64

import org.dbpedia.databus.RemoteGitlabHttpClient.{CreateFile, DeleteFile, FileAction, UpdateFile}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import sttp.client3._
import sttp.model.Uri
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scala.util.{Failure, Success, Try}

trait GitClient {

  def projectExists(name: String): Boolean

  def createProject(name: String): Try[String]

  def commitFileContent(projectName: String, name: String, data: Array[Byte]): Try[String] =
    commitSeveralFiles(projectName, Map(name -> data))

  def commitFileDelete(projectName: String, name: String): Try[String] =
    deleteSeveralFiles(projectName, Seq(name))

  def readFile(projectName: String, name: String): Try[Array[Byte]]

  def commitSeveralFiles(projectName: String, filenameAndData: Map[String, Array[Byte]]): Try[String]

  def deleteSeveralFiles(projectName: String, names: Seq[String]): Try[String]

}

//todo concurrency! limit method invocation to one simultaneous for a repo
class LocalGitClient(rootPath: Path) extends GitClient {

  override def projectExists(name: String): Boolean =
    initrepo(getRepoPathFromUsername(name)).isSuccess

  override def createProject(name: String): Try[String] =
    Try(Git.init()
      .setDirectory(getRepoPathFromUsername(name).toFile)
      .call()
    ).map(_ => name)

  override def readFile(projectName: String, name: String): Try[Array[Byte]] =
    Try(Files.readAllBytes(getFilePath(projectName, name)))

  override def commitSeveralFiles(projectName: String, filenameAndData: Map[String, Array[Byte]]): Try[String] = Try({
    val git = Git.open(getRepoPathFromUsername(projectName).toFile)
    val add = git.add()

    filenameAndData.foreach {
      case (fp, data) =>
        val pa = getRelativeFilePath(fp)
        val apa = getFilePath(projectName, fp)
        Files.createDirectories(apa.getParent)
        Files.write(apa, data)
        add.addFilepattern(pa.toString)
    }
    add.call()

    val commit = git.commit()
    val ref = commit
      .setCommitter("databus", "databus@infai.org")
      .setMessage(s"$projectName: committed ${filenameAndData.keys}")
      .call()
    ref.getName
  })

  override def deleteSeveralFiles(projectName: String, names: Seq[String]): Try[String] = Try {
    val git = Git.open(getRepoPathFromUsername(projectName).toFile)
    val rm = git.rm()
    names.foreach(fp => {
      val pa = getRelativeFilePath(fp)
      rm.addFilepattern(pa.toString)
      pa
    })
    rm.call()

    val commit = git.commit()
    val hash = commit
      .setCommitter("databus", "databus@infai.org")
      .setMessage(s"$projectName: removed $names")
      .call()
      .getName
    hash
  }

  private def getFilePath(repoName: String, filePath: String): Path = {
    val projPath = getRepoPathFromUsername(repoName)
    projPath.resolve(getRelativeFilePath(filePath))
  }

  private def getRelativeFilePath(filePath: String): Path = {
    val fp = Paths.get(filePath)
    if (fp.isAbsolute) {
      Paths.get("/").relativize(fp)
    } else {
      fp
    }
  }

  private def initrepo(pa: Path): Try[Repository] = Try(
    Git.open(pa.toFile)
      .getRepository
  )

  private def getRepoPathFromUsername(un: String) =
    rootPath.resolve(Paths.get(un))

}


class RemoteGitlabHttpClient(rootUser: String, rootPass: String, scheme: String, hostname: String, port: Option[Int]) extends GitClient {

  private val baseUri = port
    .map(p => Uri(scheme, hostname, p))
    .getOrElse(Uri(scheme, hostname))
  private val baseApiUri = baseUri.addPath("api", "v4")

  private lazy val backend = HttpURLConnectionBackend()
  private lazy val accessToken: Try[String] = Try {
    val req = authReq(rootUser, rootPass)
    backend.send(req).body match {
      case Left(e) =>
        Failure(new RuntimeException(e))
      case Right(value) =>
        val flds = for {
          JObject(ch) <- parse(value)
          JField("access_token", JString(token)) <- ch
        } yield token
        Success(flds.head)
    }
  }.flatten


  override def createProject(name: String): Try[String] = {
    val req = withAuth(basicRequest.post(baseApiUri.addPath("projects").addParam("name", name)))
    req.flatMap(r => {
      val resp = r.send(backend)
      resp.body match {
        case Left(e) => Failure(new RuntimeException(e))
        case Right(value) =>
          val flds = for {
            JObject(chl) <- parse(value)
            JField("id", JInt(id)) <- chl
          } yield id
          Success(flds.head.toString)
      }
    })
  }

  override def projectExists(name: String): Boolean =
    projectIdByName(name)
      .isSuccess

  override def readFile(projectName: String, name: String): Try[Array[Byte]] =
    projectIdByName(projectName)
      .flatMap(readSingleFile(_, name))

  override def commitSeveralFiles(projectName: String, filenameAndData: Map[String, Array[Byte]]): Try[String] =
    projectIdByName(projectName)
      .flatMap(id =>
        commitSeveralActions(id, filenameAndData.map(p => CreateFile(p._1, p._2)).toSeq)
          .orElse(commitSeveralActions(id, filenameAndData.map(p => UpdateFile(p._1, p._2)).toSeq))
      )

  override def deleteSeveralFiles(projectName: String, names: Seq[String]): Try[String] =
    projectIdByName(projectName)
      .flatMap(id => commitSeveralActions(id, names.map(p => DeleteFile(p))))

  private def projectIdByName(name: String): Try[String] = {
    val req = withAuth(
      basicRequest.get(baseApiUri
        .addPath("projects")
        .addParam("search", name))
    )
    req.flatMap(r => {
      val resp = r.send(backend)
      resp.body match {
        case Left(_) => Failure(new RuntimeException("Failed to get project id from gitlab"))
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
            .map(Success(_))
            .getOrElse(Failure(new RuntimeException("Failed to get project id from gitlab")))
      }
    })

  }

  private def commitSeveralActions(projectId: String, actions: Seq[FileAction]): Try[String] = {
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
    req.flatMap(r => {
      val resp = r.send(backend)
      resp.body match {
        case Left(e) => Failure(new RuntimeException(e))
        case Right(value) =>
          val flds = for {
            JObject(ch) <- parse(value)
            JField("id", JString(id)) <- ch
          } yield id
          Success(flds.head)
      }
    })
  }

  private def withAuth[A1, A2](req: Request[A1, A2]): Try[Request[A1, A2]] =
    accessToken.map(t => req.header("Authorization", s"Bearer $t"))

  private def authReq(user: String, pass: String) =
    basicRequest.post(baseUri
      .addPath("oauth")
      .addPath("token")
    ).body(Map(
      "grant_type" -> "password",
      "username" -> user,
      "password" -> pass))

  private def readSingleFile(projectId: String, fn: String): Try[Array[Byte]] = {
    val req = withAuth(
      basicRequest.get(baseApiUri
        .addPath("projects")
        .addPath(projectId)
        .addPath("repository")
        .addPath("files")
        .addPath(fn)
        .addParam("ref", "master"))
    )
    req.flatMap(r => {
      val resp = r.send(backend)
      resp.body match {
        case Left(e) => Failure(new RuntimeException(e))
        case Right(value) =>
          val flds = for {
            JObject(ch) <- parse(value)
            JField("content", JString(content)) <- ch
          } yield content
          Try(Base64.getDecoder.decode(flds.head))
      }
    })
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
