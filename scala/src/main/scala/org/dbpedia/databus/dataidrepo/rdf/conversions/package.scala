package org.dbpedia.databus.dataidrepo.rdf

import org.dbpedia.databus.dataidrepo.errors
import org.dbpedia.databus.dataidrepo.errors.DataIdRepoError
import org.dbpedia.databus.dataidrepo.helpers.conversions.TapableW

import com.typesafe.scalalogging.LazyLogging
import org.apache.jena.query.{Dataset, TxnType}
import org.apache.jena.rdf.model._
import resource.{Resource => _, _}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}


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


  implicit class RDFResourceW(val res: Resource) extends AnyVal {

    def getRequiredFunctionalProperty(prop: Property)
      (implicit errorGen: String => DataIdRepoError) = {

      res.listProperties(prop).asScala.toList match {

        case singleStmt :: Nil => ObjectInStatement(singleStmt)

        case Nil => throw errorGen(s"no ${prop.getURI} value for $res")

        case _ => throw errorGen(s"several ${prop.getURI} values for $res")
      }
    }
  }

  trait NodeInStatement[N <: RDFNode] {

    def node: N

    def statement: Statement
  }

  case class SubjectInStatement(statement: Statement) extends NodeInStatement[Resource] {

    def node = statement.getSubject
  }

  case class ObjectInStatement(statement: Statement) extends NodeInStatement[RDFNode] {

    def node = statement.getObject
  }

  trait ResourceCoercions {

    def nodeInStmt: NodeInStatement[_ <: RDFNode]

    def node = nodeInStmt.node

    def statement = nodeInStmt.statement

    def coerceUriResource = if(node.isURIResource) Success(node.asResource()) else {
      Failure(errors.unexpectedRdfFormat(
        s"$node is not an URI-resource:\n$statement"))
    }
  }


  implicit class NodeCoercion(val nodeInStmt: NodeInStatement[RDFNode]) extends ResourceCoercions {

    def coerceResource = Try(node.asResource()).recoverWith {

      case rre: ResourceRequiredException => Failure(errors.unexpectedRdfFormat(
        s"$node is not a resource:\n$statement"))

      case ex => Failure(ex)
    }

    def coerceLiteral = Try(node.asLiteral()).recoverWith {

      case rre: ResourceRequiredException => Failure(errors.unexpectedRdfFormat(
        s"$node is not a literal:\n$statement"))

      case ex => Failure(ex)
    }
  }

  implicit class ResourceCoercion(val nodeInStmt: NodeInStatement[Resource]) extends ResourceCoercions {

    def resource = node

  }

}
