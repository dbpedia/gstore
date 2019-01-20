package org.dbpedia.databus

import better.files.File
import com.google.common.base.Stopwatch
import com.markatta.timeforscala
import com.markatta.timeforscala._
import monix.eval.Task
import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.Scheduler
import monix.execution.atomic.Atomic
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.jena.query.ReadWrite._
import org.apache.jena.query.{Dataset, ReadWrite}
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.apache.logging.log4j.scala.Logging
import org.scalactic.Requirements._
import resource.{Resource, managed}
import virtuoso.jena.driver.VirtDataset

import scala.collection.concurrent.TrieMap
import scala.math.{max, min}
import scala.util.control.NonFatal

import java.nio.charset.StandardCharsets
import java.time.{Instant, Duration => JavaDuration}

package object vosloader extends Logging {

  type DocumentName = String

  val fileStorageDir = config.persistence.fileSystemStorageLocation

  val (loadingDir, loadedDir, failedDir) =
    (queueDir("loading"), queueDir("loaded"), queueDir("failed"))

  lazy val minUnmodifiedDurationInStorage = timeforscala.Duration(config.loading.minUnmodifiedDurationInStorage)

  lazy val loadsInProgress = TrieMap[DocumentName, Stopwatch]()

  def loadPendingDocuments(): Unit = {

    collectDocumentsToLoad.map { docDirSubmitted =>

      val docNameMemo = Atomic(None: Option[DocumentName])

      val docDirLoadingMemo = Atomic(None: Option[File])

      def docDirSubmittedOrLoading = docDirLoadingMemo.get match {

        case Some(loadingDir) => loadingDir

        case None => docDirSubmitted
      }

      val readIntoModel = Task({

        val modelName = (docDirSubmitted / s"${docDirSubmitted.name}.graph").lines.head
        docNameMemo.set(Some(modelName))

        if(loadsInProgress contains modelName) {

          logger.warn(s"Skipping load of new document for $modelName since a previous VOS load for it is still ongoing")
          None
        } else {

          requireState(loadsInProgress.put(modelName, Stopwatch.createStarted()).isEmpty,
            s"already running for $modelName")

          val docDirLoading = loadingDir / docDirSubmitted.name

          requireState(!docDirLoading.exists, s"old '${docDirSubmitted.name}' still in loading directory")
          FileUtils.moveDirectory(docDirSubmitted.toJava, docDirLoading.toJava)

          docDirLoadingMemo.set(Some(docDirLoading))

          val ttlFile = docDirLoading / s"${docDirLoading.name}.ttl"

          val model = ModelFactory.createDefaultModel()

          RDFDataMgr.read(model, ttlFile.url.toString, Lang.TURTLE)

          Some(modelName -> model)
        }
      }).executeOn(Scheduler.global)

      readIntoModel.flatMap({

        case Some((modelName, model)) =>
          //after letting the VOS driver load the model, it is moved to the loaded directory in a seperate scheduler
          loadDocument(modelName, model).map({ _ =>
            logger.info(s"SUCCESS, loaded $modelName")
            moveDocumentToLoaded(docDirSubmittedOrLoading)
          })

        case None => Task.eval(())
      }).onErrorRecover({
        case NonFatal(error) =>
          logger.error(s"FAILURE ${docDirSubmittedOrLoading.name}: $error")

          moveDocumentToFailed(docDirSubmittedOrLoading, error)
      }).doOnFinish({ _ =>
        Task({
          requireState(docDirSubmittedOrLoading.notExists, s"$docDirSubmittedOrLoading was not moved away")
          docNameMemo.get.foreach { docName =>
            requireState(loadsInProgress.remove(docName).isDefined, s"cannot unregister missing $docName")
          }
          logger.debug(s"handling of directory $docDirSubmitted finished")
        }).executeOn(Scheduler.global)
      }).runAsync(Scheduler.global)
    }
  }


  def collectDocumentsToLoad = {

    def expectedFiles(dir: File) = List(dir / s"${dir.name}.ttl", dir / s"${dir.name}.graph")

    def hasExpectedFilesNotTooRecentlyUpdated(dir: File) = expectedFiles(dir).forall {

      file => {

        val unmodifiedSince = JavaDuration.between(file.lastModifiedTime, Instant.now()).abs()

        file.isRegularFile && (unmodifiedSince >= minUnmodifiedDurationInStorage)
      }
    }

    val docsToLoad = fileStorageDir.children.filter({ dir =>

      dir.isDirectory && hasExpectedFilesNotTooRecentlyUpdated(dir)
    }).toList

    def skippedDocs = fileStorageDir.children.count(_.isDirectory) - docsToLoad.size

    logger.debug(s"Collected ${docsToLoad.size} documents to load; skipped/ignored ${skippedDocs} directories")

    docsToLoad
  }


  def loadDocument(targetNs: String, document: Model) = {

    logger.debug(s"loadDocument: $targetNs")

    vosSession { session =>

      logger.debug(s"updating named model: $targetNs")

      session.inTransaction(WRITE) { txn =>

        // keeping this code for now to test later how stable the logic behaves when uploads take longer than the
        // loadingInterval
        /*val sleepDuration = (6 + Random.nextInt(6)).seconds

        logger.debug(s"starting sleep of $sleepDuration")
        Thread.sleep(sleepDuration.toMillis)
        logger.debug(s"ended sleep of $sleepDuration")*/

        txn.updateNamedModel(targetNs, document)
      }
    }
  }

  def moveDocumentToLoaded(documentDir: File): Unit = {

    val loadedTargetDir = loadedDir / documentDir.name

    loadedTargetDir.delete(true)

    FileUtils.moveDirectory(documentDir.toJava, loadedTargetDir.toJava)
  }

  def moveDocumentToFailed(documentDir: File, error: Throwable): Unit = {

    val failedTargetDir = failedDir / documentDir.name

    failedTargetDir.delete(true)

    FileUtils.moveDirectory(documentDir.toJava, failedTargetDir.toJava)

    val errorFile = failedTargetDir / s"${documentDir.name}.error"

    errorFile.write(ExceptionUtils.getStackTrace(error))
  }

  def vosSessionsSchedulerSize = poolSizeByProcessorRatio(config.virtuoso.concurrentConnectionsToProcessorsRatio,
    1, config.virtuoso.maxConcurrentConnections)

  lazy val vosSessionsScheduler = Scheduler.fixedPool("vos-session", vosSessionsSchedulerSize,
    executionModel = AlwaysAsyncExecution)

  def vosSession[T](work: Dataset => T) = {

    def virtDs = new VirtDataset(config.virtuoso.host, config.virtuoso.user, config.virtuoso.password)

    Task({
      managed(virtDs) apply (work)
    }).executeOn(vosSessionsScheduler)
  }


  implicit class DatasetW(val ds: Dataset) extends Logging {

    def updateNamedModel(modelName: String, model: Model): Dataset = {
      Option(ds.getNamedModel(modelName)) match {

        case Some(existingModel) => {
          existingModel.removeAll()
          existingModel.add(model)
        }

        case None => ds.addNamedModel(modelName, model)
      }

      ds
    }

    def inTransaction[T](txnType: ReadWrite)(work: Dataset => T): T = {

      implicit val txnResource = new DatasetTransactionResource(txnType)

      managed(ds) apply (work)
    }
  }


  class DatasetTransactionResource(txnType: ReadWrite) extends Resource[Dataset] {

    override def open(r: Dataset): Unit = {
      logger.debug(s"starting txn: $txnType")
      r.begin(txnType)
    }

    override def close(r: Dataset): Unit = {
      logger.debug(s"committing txn: $txnType")
      r.commit()
    }

    override def closeAfterException(r: Dataset, t: Throwable): Unit = {
      logger.debug(s"aborting txn: $txnType")
      r.abort()
    }
  }

  def poolSizeByProcessorRatio(processorRatio: Double, minSize: Int = 1, maxSize: Int = 8) = {

    def poolSizeByRatio = (Runtime.getRuntime.availableProcessors * processorRatio).ceil.toInt

    min(max(minSize, poolSizeByRatio), maxSize)
  }

  implicit class TapableW[T](val anyVal: T) extends AnyVal {

    def tap(work: T => Unit) = {
      work apply anyVal
      anyVal
    }
  }

  object Implicits {

    implicit lazy val defaultUTF8 = StandardCharsets.UTF_8
  }

  protected def queueDir(dirName: String) = {

    (config.loading.vosQueuesParentDir / dirName).createIfNotExists(true)
  }
}
