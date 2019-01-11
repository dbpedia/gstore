package org.dbpedia.databus.vosloader

import com.typesafe.scalalogging.LazyLogging
import monix.execution.Scheduler

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.Success

object Main extends LazyLogging {

  lazy val loadingActiceFlagFile = (config.loading.vosQueuesParentDir / "loading-active.flag")

  def main(args: Array[String]): Unit = {

    //TODO: to make the main loop more robust for restarts, document dirs in the loading queue dir should be
    // moved back to the submission dir (if no new directory with the same arrived there in the meantime) or
    // be deleted otherwise

    loadingActiceFlagFile.createIfNotExists()

    // condition to end the main loading process, gets completed once deletion of the flag file is found
    val stopLoadingPromise = Promise[Unit]()

    val loadingLoop =  Scheduler.global.scheduleWithFixedDelay(1 second, config.loading.loadingInterval) {

      loadPendingDocuments()
    }

    val checkForStopLoop = Scheduler.global.scheduleWithFixedDelay(0 millis, 500 millis) {

      if(!(stopLoadingPromise.isCompleted) && (loadingActiceFlagFile notExists)) {
        logger.info("Flag file to continue loading not found anymore - ending loading process...")
        loadingLoop.cancel()
        stopLoadingPromise.complete(Success(()))
      }
    }

    Await.ready(stopLoadingPromise.future, Duration.Inf)
    checkForStopLoop.cancel()
    logger.info("Loading process ended.")
  }
}
