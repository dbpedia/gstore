package org.dbpedia.databus.dataidrepo.models

import org.dbpedia.databus.dataidrepo.errors
import org.dbpedia.databus.dataidrepo.errors.DataIdRepoError
import org.dbpedia.databus.dataidrepo.{DataIdRepo, errors}
import org.dbpedia.databus.dataidrepo.rdf.conversions._
import org.dbpedia.databus.dataidrepo.rdf.vocab._

import org.apache.jena.rdf.model.{Model, Resource}
import org.apache.jena.vocabulary.RDF

import scala.collection.JavaConverters._

class DataIdDocument(rdf: Model) {

  def getSingleFiles = {

    val typeStatements = rdf.listStatements(null, RDF.`type`, dataid.SingeFile).asScala

    typeStatements map { stmt => new SingleFile(stmt.getSubject) }
  }


}

class SingleFile(res: Resource) {

  implicit def errorGen: String => DataIdRepoError = errors.unexpectedDataIdFormat _

  def associatedAgent = res.getRequiredFunctionalProperty(dataid.associatedAgent)

  def downloadURL = res.getRequiredFunctionalProperty(dcat.downloadURL)

  def signatureString = res.getRequiredFunctionalProperty(dataid.signature)
}
