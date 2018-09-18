package org.dbpedia.databus.dataidrepo.rdf

import org.dbpedia.databus.dataidrepo.rdf.conversions._
import org.dbpedia.databus.shared.helpers.conversions._

import better.files.File
import com.typesafe.scalalogging.LazyLogging
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.execution.atomic.Atomic
import org.apache.jena.query.{Dataset, TxnType}
import org.apache.jena.rdf.model.{Model, ModelFactory, Resource}
import org.apache.jena.tdb2.TDB2Factory
import org.scalatest.{FlatSpec, Matchers}
import org.scalactic.Requirements._
import org.scalactic.Snapshots._
import org.scalactic.TypeCheckedTripleEquals._


import scala.collection.JavaConverters._
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.concurrent.duration._
import scala.util.{Random, Try}
import scala.language.postfixOps


import java.util.concurrent.{Callable, CompletableFuture, Executors, Future}
import java.util.concurrent.CompletableFuture._
import java.util.function.Supplier

class TDBTransactionsTest extends FlatSpec with Matchers with LazyLogging {

  val shouldBePersistedStr = "urn:shouldBePersisted"

  case class WorkedTDB(dataset: Dataset, transactionPerformed: Int, successfulTransactions: Int)

  def checkOnWorkedTDB(checks: WorkedTDB => Unit) = {

    val allTransactions = Random.nextInt(10) + 10

    val ds = TDB2Factory.createDataset()

    val sequenceGen = Atomic(0)
    val successCounter = Atomic(0)

    val transactions = List.fill(allTransactions) {

      Task {
        val withSuccess = Random.nextBoolean()
        val allowedString = if(withSuccess) "allowed" else "illegal"
        val allowedIRI = s"urn:${sequenceGen.incrementAndGet()}-$allowedString"

        if(withSuccess) successCounter.increment()

        def addAllowedStatement(model: Model) = {

          val prop = model.createProperty(shouldBePersistedStr)

          model.createResource(allowedIRI).addLiteral(prop, withSuccess)
        }

        ds writeTransaction { dataset =>

          val namedModel = ModelFactory.createDefaultModel().tap(addAllowedStatement)

          ds.addNamedModel(allowedIRI, namedModel)
          addAllowedStatement(ds.getDefaultModel)

          if(!withSuccess) {
            sys.error("error to test rollback")
          }
        }
      } onErrorRecover {
        case re: RuntimeException if re.getMessage == "error to test rollback" => Unit
      }
    }

    val checked = Task.gatherUnordered(Random.shuffle(transactions)).map { _ =>

      ds.readTransaction { dataset =>
        checks(WorkedTDB(dataset, allTransactions, successCounter.get))
      }
    }

    Await.result(checked.runAsync, 10.minutes)
  }

  "A TDB with several transaction blocks performed" should
    "have the number of statements in the default graph and the number of names graphs " +
      "equal to successful transactions" in {

    checkOnWorkedTDB { case WorkedTDB(ds, transactions, successful) =>

      ds.getDefaultModel.listStatements().asScala.size should equal(successful)

      ds.listNames().asScala.size should equal(successful)
    }
  }

  it should s"contain only 'true' values for $shouldBePersistedStr" in {

    checkOnWorkedTDB { case WorkedTDB(ds, _, _) =>

      val unionModel = ds.getUnionModel

      val set = unionModel.listObjectsOfProperty(unionModel.createProperty(shouldBePersistedStr)).asScala.toSet

      set.map(_.asLiteral().getBoolean) should equal(Set(true))
    }
  }

  it should "not contain graph names containing the substring 'illegal'" in {

    checkOnWorkedTDB { case WorkedTDB(ds, _, _) =>

      ds.listNames().asScala.find(_ contains "illegal") should be(None)
    }
  }

  "In a minimal exchange threads scenario there" should "be havoc concering transaction" in {

    val tdb = TDB2Factory.connectDataset(File.newTemporaryDirectory().pathAsString)

    val executorA = Executors.newSingleThreadExecutor()
    val executorB = Executors.newSingleThreadExecutor()


    val writePromiseA = Promise[Resource]()
    val readPromiseB = Promise[Unit]()

    val valueProp = tdb.getDefaultModel.createProperty("<urn:value>")


    val writeAFut: CompletableFuture[Integer] = supplyAsync(() => {

      writePromiseA.complete(Try {
        logger.debug("starting write transaction " + snap(tdb.isInTransaction, tdb.transactionMode, Thread.currentThread))
        tdb.begin(TxnType.WRITE)
        logger.debug("adding statement " + snap(tdb.isInTransaction, tdb.transactionMode, Thread.currentThread))
        tdb.getDefaultModel.createResource("<urn:A>").addLiteral(valueProp, "A").tap { res =>
        }
      })

      logger.debug("reading from B complete:" + Await.result(readPromiseB.future, 5 seconds)
        + "\n" + snap(tdb.isInTransaction, tdb.transactionMode, Thread.currentThread))

      tdb.commit()
      1
    }, executorA)

    val readBFut: CompletableFuture[Integer] = supplyAsync(() => {

      readPromiseB.complete(Try {
        logger.debug("starting read transaction " + snap(tdb.isInTransaction, tdb.transactionMode, Thread.currentThread))
        tdb.begin(TxnType.READ)
        logger.debug("add commands for resource complete:" + Await.result(writePromiseA.future, 5 seconds).getURI
          + "\n" + snap(tdb.isInTransaction, tdb.transactionMode, Thread.currentThread))
        logger.debug("statemens seen from B:\n" +
          tdb.getDefaultModel.createResource("<urn:A>").listProperties().asScala.mkString("\n"))

      })

//      tdb.end()
      2
    }, executorB)

    val commitA = writeAFut.thenRunAsync(() => {
      logger.debug("commit for A " + snap(tdb.isInTransaction, tdb.transactionMode, Thread.currentThread))
      tdb.commit
      tdb.end()
    }, executorA)

    val endB = readBFut.thenRunAsync(() => {
      logger.debug("txn end for B " + "\n" + snap(tdb.isInTransaction, tdb.transactionMode, Thread.currentThread))
      tdb.end
    }, executorB)

    CompletableFuture.allOf(writeAFut, readBFut).join()

    val persistedStatements = {
      tdb.begin(TxnType.READ)
      val stmts = tdb.getDefaultModel.listStatements().asScala.toList
      tdb.end()
      stmts
    }

    logger.debug("persisted statements:\n" + persistedStatements.mkString("\n"))

    persistedStatements should have size (1)

    persistedStatements.exists({
      _.getObject.asLiteral().getString == "A"
    }) should be(true)

    persistedStatements.exists({
      _.getObject.asLiteral().getString == "B"
    }) should be(false)
  }
}
