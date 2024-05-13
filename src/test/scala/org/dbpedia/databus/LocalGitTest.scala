package org.dbpedia.databus

import java.nio.file.Paths

import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.{CanonicalTreeParser, TreeWalk}
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scala.collection.JavaConverters._

class LocalGitTest extends FlatSpec with Matchers with BeforeAndAfter {

  val root = getClass.getClassLoader.getResource(".").toURI
  val rp = Paths.get(root).resolve("test_root")

  before({
    FileUtils.deleteDirectory(rp.toFile)
  })

  "Client" should "create and check project" in {
    val cli = new LocalGitClient(rp)

    val re = cli.createProject("gut")
    re.isSuccess should be(true)

    cli.projectExists("haha") should be(false)
    cli.projectExists("gut") should be(true)
  }

  "Client" should "commit with different author names and emails" in {
    val cli = new LocalGitClient(rp)
    val pn = "test"
    cli.createProject(pn)
    Seq(
      cli.commitSeveralFiles(pn, Map("haha.txt" -> "lol".getBytes), None, None),
      cli.commitSeveralFiles(pn, Map("haha.txt" -> "lol".getBytes), None, Some("bla")),
      cli.commitSeveralFiles(pn, Map("haha.txt" -> "lol".getBytes), Some("Bla1"), None),
      cli.commitSeveralFiles(pn, Map("haha.txt" -> "lol".getBytes), Some("Bla2"), Some("")),
      cli.commitSeveralFiles(pn, Map("haha.txt" -> "lol".getBytes), Some("Bla3"), Some("e@mail.mail")),
      cli.commitSeveralFiles(pn, Map("haha.txt" -> "lol".getBytes), Some("Bla4"), Some("stri")),
    ).foreach(_.isSuccess should be(true))

    val ts = Seq(
      ("Bla4", "stri"),
      ("Bla3", "e@mail.mail"),
      ("Bla2", ""),
      ("Bla1", ""),
      ("bla", "bla"),
      ("databus", "databus@infai.org")
    )

    allCommits(pn).zip(ts).foreach(c => {
      c._1.getAuthorIdent.getName should be(c._2._1)
      c._1.getAuthorIdent.getEmailAddress should be(c._2._2)
    })

  }

  "Client" should "commit twice" in {
    val cli = new LocalGitClient(rp)

    val pn = "test"
    cli.createProject(pn)
    val re2 = cli.commitSeveralFiles(pn, Map("haha.txt" -> "lol".getBytes, "/loldata/lol.txt" -> "haha".getBytes), Some("Bla"), None)
    val re = cli.commitSeveralFiles(pn, Map("haha.txt" -> "lol".getBytes, "/loldata/lol.txt" -> "haha".getBytes), Some("Bla"), None)
    re2.isSuccess should be(true)
    re.isSuccess should be(true)

    val rer = cli.readFile(pn, "haha.txt")
    rer.isSuccess should be(true)
    rer.get should be("lol".getBytes())
    val rer2 = cli.readFile(pn, "hahah.txt")
    rer2.isSuccess should be(false)
    val rer3 = cli.readFile(pn, "/loldata/lol.txt")
    rer3.isSuccess should be(true)
    rer3.get should be("haha".getBytes())

    val files = Set(
      "haha.txt",
      "loldata/lol.txt",
      "loldata",
      "lol.txt"
    )

    val treeWalk = treeWalkForLastCommit(pn)

    treeWalk.next()
    files should contain(treeWalk.getNameString)
    treeWalk.next()
    files should contain(treeWalk.getNameString)
    treeWalk.next()
    files should contain(treeWalk.getNameString)
    treeWalk.next()
    files should contain(treeWalk.getNameString)
    treeWalk.next() should be(false)
  }

  "Client" should "delete twice" in {
    val cli = new LocalGitClient(rp)

    val pn = "test"
    cli.createProject(pn)
    val re = cli.commitSeveralFiles(pn, Map("haha.txt" -> "lol".getBytes, "/loldata/lol.txt" -> "haha".getBytes), Some("Bla"), None)
    re.isSuccess should be(true)


    val files = Set(
      "haha.txt",
      "loldata/lol.txt",
      "loldata",
      "lol.txt"
    )

    val re2 = cli.deleteSeveralFiles("test", files.toSeq, Some("Bla"), None)
    re2.isSuccess should be(true)
    val re3 = cli.deleteSeveralFiles("test", files.toSeq, Some("Bla"), None)
    re3.isSuccess should be(true)
  }


  "Client" should "commit files" in {
    val cli = new LocalGitClient(rp)

    val pn = "test"
    cli.createProject(pn)
    val re = cli.commitSeveralFiles(pn, Map("haha.txt" -> "lol".getBytes, "/loldata/lol.txt" -> "haha".getBytes), Some("Bla"), None)
    re.isSuccess should be(true)


    val files = Set(
      "haha.txt",
      "loldata/lol.txt",
      "loldata",
      "lol.txt"
    )

    val treeWalk = treeWalkForLastCommit(pn)

    treeWalk.next()
    files should contain(treeWalk.getNameString)
    treeWalk.next()
    files should contain(treeWalk.getNameString)
    treeWalk.next()
    files should contain(treeWalk.getNameString)
    treeWalk.next()
    files should contain(treeWalk.getNameString)
    treeWalk.next() should be(false)


    val red = cli.deleteSeveralFiles(pn, files.toSeq, Some("Bla"), None)
    red.isSuccess should be(true)

    val entrs2 = diff(pn)
    entrs2.isEmpty should be(false)
    entrs2.asScala
      .foreach(e => {
        e.getChangeType should be(ChangeType.DELETE)
        files should contain(e.getOldPath)
      })
  }


  def lastCommit(repoName: String) = {
    val git = Git.open(rp.resolve(repoName).toFile)
    val rw = new RevWalk(git.getRepository)
    val head = git.getRepository.resolve(Constants.HEAD)
    rw.parseCommit(head)
  }


  def allCommits(repoName: String) = {
    val git = Git.open(rp.resolve(repoName).toFile)
    git.log().call().asScala
  }

  def treeWalkForLastCommit(repoName: String) = {
    val git = Git.open(rp.resolve(repoName).toFile)
    val rw = new RevWalk(git.getRepository)
    val head = git.getRepository.resolve(Constants.HEAD)
    val commit = rw.parseCommit(head)
    val treeWalk = new TreeWalk(git.getRepository)
    treeWalk.addTree(commit.getTree)
    //    treeWalk.reset(commit.getTree.getId)
    treeWalk.setRecursive(true)
    treeWalk
  }

  def diff(repoName: String) = {
    val git = Git.open(rp.resolve(repoName).toFile)
    val reader = git.getRepository.newObjectReader
    val oldTreeIter = new CanonicalTreeParser
    val oldTree = git.getRepository.resolve("HEAD~1^{tree}")
    oldTreeIter.reset(reader, oldTree)
    val newTreeIter = new CanonicalTreeParser
    val newTree = git.getRepository.resolve("HEAD^{tree}")
    newTreeIter.reset(reader, newTree)

    val diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)
    diffFormatter.setRepository(git.getRepository)
    diffFormatter.scan(oldTreeIter, newTreeIter)
  }

}
