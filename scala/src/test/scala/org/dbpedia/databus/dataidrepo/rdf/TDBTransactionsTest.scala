package org.dbpedia.databus.dataidrepo.rdf

import org.dbpedia.databus.dataidrepo.rdf.conversions._
import org.dbpedia.databus.dataidrepo.helpers.conversions._

import monix.eval.Task
import monix.execution.atomic.Atomic
import monix.execution.Scheduler.Implicits.global
import org.apache.jena.graph.NodeFactory
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.tdb2.TDB2Factory
import org.scalatest.{FlatSpec, Matchers}

import scala.util.Random
import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by Markus Ackermann.
  */
class TDBTransactionsTest extends FlatSpec with Matchers {

  val shouldBePersistedStr = "urn:shouldBePersisted"

  case class WorkedTDB(dataset: Dataset, transactionPerformed: Int, successfulTransactions: Int)

  def checkOnWorkedTDB(checks: WorkedTDB => Unit) = {

    val allTransactions = Random.nextInt(1000) + 100

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

        ds writeTransaction {

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

      ds.readTransaction {
        checks(WorkedTDB(ds, allTransactions, successCounter.get))
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

        ds.listNames().asScala.find(_ contains "illegal") should be (None)
    }
  }
}
