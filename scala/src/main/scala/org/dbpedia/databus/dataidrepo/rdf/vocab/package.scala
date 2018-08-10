package org.dbpedia.databus.dataidrepo.rdf

import org.dbpedia.databus.dataidrepo.rdf.vocab.DataIdNs.property

/**
  * Created by Markus Ackermann.
  * No rights reserved.
  */
package object vocab {

  def dataid = DataIdNs

  def dcat = DcatNs

  object DataIdNs extends RDFNamespace {

    def namespace = "http://dataid.dbpedia.org/ns/core#"

    lazy val SingeFile = resource("SingleFile")

    lazy val associatedAgent = property("associatedAgent")

    lazy val signature = property("signature")
  }

  object DcatNs extends RDFNamespace {

    override def namespace: String = "http://www.w3.org/ns/dcat#"

    lazy val downloadURL = property("downloadURL")
  }
}
