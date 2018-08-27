package org.dbpedia.databus.dataidrepo.rdf

import org.dbpedia.databus.shared.helpers.conversions.TapableW

import com.typesafe.scalalogging.LazyLogging
import org.apache.jena.query.{Dataset, TxnType}
import resource.{Resource => _, _}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


package object conversions {

  implicit class DatasetW(val ds: Dataset) extends LazyLogging {

    def readTransaction[T](work: => T) = inTransaction(TxnType.READ)(work)

    def writeTransaction[T](work: => T) = inTransaction(TxnType.WRITE)(work)

    def inTransaction[T](txnType: TxnType)(work: => T) = {

      /*todo: It seems that transactions work properly also without nailing it to a specific thread
        -> can we rely on that ?
        -> how can this be given the use of ThreadLocal in org.apache.jena.dboe.transaction.txn.TransactionalBase ?
      */
      import org.dbpedia.databus.dataidrepo.helpers.schedulers.tdbTransactionExecutionContext

      val fut = Future {
        logger.debug("starting TDB transaction")
        managed(ds.tap(_.begin(txnType))).foreach(_ => work)
      }
      Await.result(fut, 1.minute)
    }
  }
}
