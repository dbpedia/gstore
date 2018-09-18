package org.dbpedia.databus.dataidrepo.helpers

import org.dbpedia.databus.shared.helpers.conversions._

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext

import java.util.concurrent.Executors

package object schedulers extends LazyLogging {

  lazy val tdbTransactionExecutor = {

    def threadFactory = new ThreadFactoryBuilder().tap({ builder =>
      builder.setDaemon(true)
      builder.setNameFormat("tdb-txn-%d")
    }).build()

    Executors.newCachedThreadPool(threadFactory).tap { es =>

      sys.addShutdownHook {

        logger.debug("shutting down executor service for TDB transactions")
        es.shutdown()
      }
    }
  }

  implicit lazy val tdbTransactionExecutionContext = ExecutionContext.fromExecutor(tdbTransactionExecutor)

  lazy val intervalScheduler = {

    def threadFactory = new ThreadFactoryBuilder().tap({ builder =>
      builder.setDaemon(true)
      builder.setNameFormat("interval-scheduler-%d")
    }).build()

    Executors.newSingleThreadScheduledExecutor(threadFactory).tap { es =>

      sys.addShutdownHook {

        logger.debug("shutting down interval scheduler")
        es.shutdown()
      }
    }
  }
}
