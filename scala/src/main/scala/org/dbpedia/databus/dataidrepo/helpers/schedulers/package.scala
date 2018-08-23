package org.dbpedia.databus.dataidrepo.helpers

import org.dbpedia.databus.shared.helpers.conversions._

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext

import java.util.concurrent.Executors

package object schedulers extends LazyLogging {

  implicit lazy val tdbTransactionExecutionContext = {

    def threadFactory = new ThreadFactoryBuilder().tap({ builder =>
      builder.setDaemon(true)
      builder.setNameFormat("tdb-txn-%d")
    }).build()

    val executorService = Executors.newCachedThreadPool(threadFactory).tap { es =>

      sys.addShutdownHook {

        logger.debug("shutting down executor service for TDB transactions")
        es.shutdown()
      }
    }

    ExecutionContext.fromExecutorService(executorService)
  }
}
