package org.dbpedia.databus.dataidrepo.rdf

import org.dbpedia.databus.dataidrepo.errors
import org.dbpedia.databus.dataidrepo.errors.DataIdRepoError

import org.apache.jena.rdf.model.{Property, Resource}

import scala.collection.JavaConverters._

/**
  * Created by Markus Ackermann.
  * No rights reserved.
  */
package object conversions {

  implicit class RdfResourceW(res: Resource) {

    def getRequiredFunctionalProperty(prop: Property)(implicit errorGen: String => DataIdRepoError) = res.listProperties(prop).asScala.toList match {

      case singleStmt :: Nil => {

        singleStmt.getObject match {

          case res: Resource if !res.isAnon => res.getURI

          case _ => throw errorGen(
            s"non-IRI value for ${prop.getURI}: $singleStmt")
        }
      }

      case Nil => throw errorGen(s"no ${prop.getURI} value for $res")

      case _ => throw errorGen(s"several ${prop.getURI} values for $res")
    }
  }
}
