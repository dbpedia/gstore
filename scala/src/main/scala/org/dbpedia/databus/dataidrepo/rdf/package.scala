package org.dbpedia.databus.dataidrepo

import org.apache.jena.rdf.model.ResourceFactory

import scala.language.dynamics

/**
  * Created by Markus Ackermann.
  * No rights reserved.
  */
package object rdf {

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
}
