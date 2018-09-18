package org.dbpedia.databus.dataidrepo.helpers

import org.dbpedia.databus.dataidrepo.rdf.logger
import org.dbpedia.databus.dataidrepo.helpers.conversions.TaggedW

import monix.execution.schedulers.SchedulerService
import org.apache.jena.query.Dataset
import resource.Resource
import scalaz.{@@, Tag}

import scala.util.Try

import java.util.concurrent.ExecutorService

/**
  * Created by Markus Ackermann.
  */
package object arm {

  implicit def schedulerServiceResource = new Resource[SchedulerService] {

    override def close(r: SchedulerService): Unit = r.shutdown()
  }
}
