package org.dbpedia.databus.dataidrepo

import org.dbpedia.databus.dataidrepo.helpers.conversions.TapableW

import com.typesafe.scalalogging.LazyLogging
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.tdb2.TDB2Factory
import resource.Resource

import scala.language.dynamics
import scala.util.Try

/**
  * Created by Markus Ackermann.
  * No rights reserved.
  */
package object rdf extends LazyLogging {

  trait RDFNamespace {

    def namespace: String

    def resource(suffix: String) = ResourceFactory.createResource(namespace + suffix)

    def property(suffix: String)= ResourceFactory.createProperty(namespace + suffix)

    lazy val res = new Dynamic {

      def selectDynamic(suffix: String) = resource(suffix)
    }

    lazy val prop = new Dynamic {

      def selectDynamic(suffix: String) = property(suffix)
    }
  }

  lazy val repoTDB = TDB2Factory.connectDataset(config.tdbLocation.pathAsString) tap { dataset =>

    sys addShutdownHook {
      Try(dataset.close).fold(
        ex => logger.error("Error while closing TDB store", ex),
        _ => logger.info("TDB closed (from shutdown hook)")
      )
    }
  }

  implicit def tdbTrancationResource = new Resource[Dataset] {

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
