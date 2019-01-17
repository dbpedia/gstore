package org.dbpedia.databus.vosloader

import better.files._
import com.google.common.io.Resources
import monix.execution.Scheduler
import org.apache.commons.io.{FileUtils, IOUtils}
import org.apache.logging.log4j.scala.Logging
import resource._

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.Success
import scala.util.Random

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

object TestDocumentSubmitter extends Logging {

  lazy val testDocSubmissionActiveFlagFile = (config.loading.vosQueuesParentDir / "test-docs-active.flag")

  def mammalsResource = Resources.getResource("mammals-1.0.0_dataid.ttl")

  def main(args: Array[String]): Unit = {

    def createDocDirToLoad(docName: DocumentName) = {

      val urlEncodedName = URLEncoder.encode(docName, UTF_8.name())

      File.temporaryDirectory(s"${urlEncodedName}-").foreach { tempDocDir =>

        (tempDocDir / s"${urlEncodedName}.graph").writeText(docName)

        def ttlFile = (tempDocDir / s"${urlEncodedName}.ttl")

        (managed(mammalsResource.openStream()) and managed(ttlFile.newOutputStream)) apply {

          case (resourceStream, fileSink) => resourceStream pipeTo fileSink
        }

        val targetDir = fileStorageDir / urlEncodedName

        if(targetDir.notExists) {

          FileUtils.moveDirectory(tempDocDir.toJava, targetDir.toJava)
        }
      }
    }

    testDocSubmissionActiveFlagFile.createIfNotExists()

    // condition to end the main loading process, gets completed once deletion of the flag file is found
    val stopLoadingPromise = Promise[Unit]()

    def documentSubmitInterval = (config.loading.loadingInterval * 0.8).asInstanceOf[FiniteDuration]

    val pushDocsLoop =  Scheduler.global.scheduleWithFixedDelay(1 second, documentSubmitInterval) {

      Random.shuffle((1 to 4).toList).take(Random.nextInt(4)).foreach { k =>

        val graphName = s"urn:mammals-$k"

        createDocDirToLoad(graphName)
      }
    }

    val checkForStopLoop = Scheduler.global.scheduleWithFixedDelay(0 millis, 500 millis) {

      if(!(stopLoadingPromise.isCompleted) && (testDocSubmissionActiveFlagFile notExists)) {
        logger.info("Flag file to continue loading not found anymore - ending document submission process...")
        pushDocsLoop.cancel()
        stopLoadingPromise.complete(Success(()))
      }
    }

    Await.ready(stopLoadingPromise.future, Duration.Inf)
    checkForStopLoop.cancel()
    logger.info("Document submission ended.")
  }
}
