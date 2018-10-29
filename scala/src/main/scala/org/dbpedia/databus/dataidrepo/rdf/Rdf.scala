package org.dbpedia.databus.dataidrepo.rdf

import org.dbpedia.databus.dataidrepo.config.DataIdRepoConfig
import org.dbpedia.databus.shared.helpers.conversions.TapableW

import com.typesafe.scalalogging.LazyLogging
import org.apache.jena.query.Dataset
import org.apache.jena.tdb
import org.apache.jena.tdb.TDBFactory
import resource.Resource

import scala.language.dynamics
import scala.util.Try

/**
  * Created by Markus Ackermann.
  * No rights reserved.
  */
class Rdf(implicit config: DataIdRepoConfig) extends LazyLogging {

  lazy val repoTDB = {

    logger.info(s"TDB location: ${config.persistence.tdbLocation.pathAsString}")

    config.persistence.tdbLocation.createDirectories()

    TDBFactory.createDataset(config.persistence.tdbLocation.pathAsString) tap { dataset =>

      sys addShutdownHook {
        Try(dataset.close).fold(
          ex => logger.error("Error while closing TDB store", ex),
          _ => logger.info("TDB closed (from shutdown hook)")
        )
      }
    }
  }

  implicit def tdbTransactionResource = new Resource[Dataset] {

    override def closeAfterException(r: Dataset, t: Throwable): Unit = {
      logger.warn("TDB rollback after error", t)
      r.abort()
    }

    override def close(r: Dataset): Unit = {
      logger.debug(s"TDB commit")
      r.commit()
    }
  }
}
